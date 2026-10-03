package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.ui.MainTab
import com.jmcomic_next.lyqs.ui.slidingTabRoutesOf
import com.jmcomic_next.lyqs.ui.tabShiftOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tab 切换的方向规则（1.4.3）。
 *
 * 这条规则值得单测，因为它有两个都**不会崩、只会看着别扭**的失效方式：
 *
 *  1. **方向反了** —— 往右边的 Tab 切，新内容却从左边进来。用户看到的不是"切到旁边一栏"，
 *     而是"页面莫名其妙飘了一下"。方向必须由两个 Tab 在底栏里的先后位置算出来。
 *  2. **判定太宽** —— 把一次普通的 push 当成 Tab 切换。详情页的标签就是 push 到搜索页的
 *     （`search?q=…`），那时初始态是详情而不是 Tab；只看"目标是不是搜索 Tab"
 *     就会给一次正常的"进详情"平白加上横滑。
 *
 * 所以这里既钉方向，也钉"什么不算 Tab 切换"。
 */
class NavigationMotionTest {

    private val home = MainTab.Home.pattern
    private val category = MainTab.Category.pattern
    private val search = MainTab.Search.pattern
    private val profile = MainTab.Profile.pattern

    @Test
    fun `切到右边的 Tab 是正方向`() {
        // 底栏从左到右：首页 / 分类 / 搜索 / 我的
        assertEquals(1, tabShiftOf(home, category))
        assertEquals(1, tabShiftOf(category, search))
        assertEquals(1, tabShiftOf(search, profile))
        assertEquals(1, tabShiftOf(home, search))
    }

    @Test
    fun `切到左边的 Tab 是负方向`() {
        assertEquals(-1, tabShiftOf(profile, search))
        assertEquals(-1, tabShiftOf(search, category))
        assertEquals(-1, tabShiftOf(category, home))
        assertEquals(-1, tabShiftOf(profile, home))
    }

    @Test
    fun `跨多个 Tab 也只取方向`() {
        // 位移比例固定 0.3 屏宽：若按下标差放大，首页 -> 我的 会滑 0.9 屏，比淡入还生硬
        assertEquals(1, tabShiftOf(home, profile))
        assertEquals(-1, tabShiftOf(profile, home))
    }

    @Test
    fun `同一个 Tab 不是切换`() {
        assertEquals(0, tabShiftOf(home, home))
        assertEquals(0, tabShiftOf(search, search))
    }

    @Test
    fun `从别的入口进搜索不算 Tab 切换`() {
        // 详情页 / 阅读页的标签、评论页 —— 都会 push 到 `search?q=…`。
        // 初始态不是主 Tab，所以这一次不能横滑（否则"进详情"会平白飘一下）。
        assertEquals(0, tabShiftOf("detail/123", search))
        assertEquals(0, tabShiftOf("read/123/456", search))
        assertEquals(0, tabShiftOf("comments/123", search))
        assertEquals(0, tabShiftOf("more/26?title=%E5%91%A8%E5%88%8A", search))
        assertEquals(0, tabShiftOf("favorites", search))
    }

    @Test
    fun `从 Tab 去别的目的地不算 Tab 切换`() {
        // 这些页面之间是"整屏对整屏"：位移为零，只能淡入淡出 + 共享元素
        assertEquals(0, tabShiftOf(home, "detail/123"))
        assertEquals(0, tabShiftOf(category, "auth?reason="))
        assertEquals(0, tabShiftOf(profile, "favorites"))
        assertEquals(0, tabShiftOf(search, "detail/123"))
    }

    @Test
    fun `非 Tab 之间也不算 Tab 切换`() {
        assertEquals(0, tabShiftOf("detail/1", "detail/2"))
        assertEquals(0, tabShiftOf("detail/1", "read/1/2"))
        assertEquals(0, tabShiftOf(null, profile))
        assertEquals(0, tabShiftOf(home, null))
    }

    @Test
    fun `搜索页带不带查询参数都是同一个 Tab`() {
        // `destination.route` 给的是注册的模式 `search?q={q}`，但比对时按 `?` 之前
        // 那一段算身份：否则"同一个搜索 Tab 换个关键词"会被当成一次左右横滑。
        assertEquals(0, tabShiftOf("search?q=%E6%A0%87%E9%A2%98", search))
        assertEquals(0, tabShiftOf("search?q={q}", "search?q=abc"))
        // 导航用的 route（不带查询串）与注册的模式指的是同一个 Tab
        assertEquals(-1, tabShiftOf(MainTab.Search.route, home))
        assertEquals(1, tabShiftOf(home, MainTab.Search.route))
    }

    // ---- 横滑期间哪些页面不参与共享元素 ----
    //
    // 共享元素的匹配只看 key。横滑时出页与入页同时挂在组合树上，同一部作品如果
    // 两边都有，两张卡就挂着同一个 key，Compose 会播一次"从出页的卡飞进入页的卡"。
    // 这里钉住"哪些帧算横滑"：多算了会把"列表 → 详情"的封面动画一起掐掉，
    // 少算了就漏掉那一次假动画 —— 两种都不会崩，只会在屏幕上说话。

    @Test
    fun `两个主 Tab 同时可见才算横滑`() {
        assertEquals(setOf(home, category), slidingTabRoutesOf(listOf(home, category)))
        assertEquals(setOf(home, search), slidingTabRoutesOf(listOf(search, home)))
        // 打断式的连续切栏：三个都在转场里，三个都算
        assertEquals(
            setOf(home, category, search),
            slidingTabRoutesOf(listOf(home, category, search)),
        )
        // 同一个 Tab（搜索页两种写法）不算两个
        assertEquals(emptySet<String>(), slidingTabRoutesOf(listOf(search, "search?q={q}")))
    }

    @Test
    fun `停在一栏不是横滑 - 另一个 Tab 留在栈里也不算`() {
        // visibleEntries 是"当前目的地 + 正在转场的"，不是整个返回栈：
        // 停在分类、首页还在栈里时，这里只会拿到一个路由。
        assertEquals(emptySet<String>(), slidingTabRoutesOf(listOf(home)))
        assertEquals(emptySet<String>(), slidingTabRoutesOf(listOf(category)))
        assertEquals(emptySet<String>(), slidingTabRoutesOf(emptyList()))
        assertEquals(emptySet<String>(), slidingTabRoutesOf(listOf(null, home)))
    }

    @Test
    fun `列表与详情之间不是横滑 - 封面动画必须照常`() {
        // 进详情（列表 → 详情）与返回（详情 → 列表）都不许被当成横滑
        assertEquals(
            emptySet<String>(),
            slidingTabRoutesOf(listOf(home, "detail/{id}?cover={cover}&title={title}")),
        )
        assertEquals(
            emptySet<String>(),
            slidingTabRoutesOf(listOf("detail/{id}?cover={cover}&title={title}", home)),
        )
        // 详情页的标签 push 到搜索页：一个主 Tab + 一个别的目的地，也不是横滑
        assertEquals(emptySet<String>(), slidingTabRoutesOf(listOf("detail/123", search)))
        assertEquals(emptySet<String>(), slidingTabRoutesOf(listOf("more/26?title=%E5%91%A8%E5%88%8A", search)))
    }
}
