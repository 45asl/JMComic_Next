package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.ui.components.clampPill
import com.jmcomic_next.lyqs.ui.components.nearestTab
import com.jmcomic_next.lyqs.ui.components.tabIndexAt
import com.jmcomic_next.lyqs.ui.components.tabSelectionWeight
import com.jmcomic_next.lyqs.ui.tabSlideDurationMs
import com.jmcomic_next.lyqs.ui.tabSlideEnterOffset
import com.jmcomic_next.lyqs.ui.tabSlideExitOffset
import com.jmcomic_next.lyqs.ui.theme.MotionSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Tab 切换的动效规则（1.5.0，按 KernelSU 的 HorizontalPager 重做）。
 *
 * 这一批数字值得单测，因为它们在代码里都是"看不出错"的：
 *
 *  1. **位移比例**：0.3 屏与 1 屏都编译得过、也都有动画，区别只在观感 ——
 *     0.3 屏时两页会在屏幕中间叠一条，正是"切换动画特别奇怪"的来源。
 *     这里钉住"整屏"与"两页始终拼成一条、中间没有缝也没有叠"。
 *  2. **时长**：整屏位移比挪 30% 走得远，时长必须跟着加长，否则像闪一下。
 *  3. **胶囊位置**：它是一个 Float（可以停在两栏之间），吸附、夹取、染色权重
 *     全是纯算术，错了不会崩，只会"点第三栏停在第二栏"或"颜色提前跳过去"。
 */
class TabPagerMotionTest {

    private val width = 1080

    // ---- Tab 页面横滑的几何 ----

    @Test
    fun `往右边的 Tab 切就从右边整屏进来`() {
        assertEquals(width, tabSlideEnterOffset(shift = 1, width = width))
        assertEquals(-width, tabSlideExitOffset(shift = 1, width = width))
    }

    @Test
    fun `往左边的 Tab 切方向相反`() {
        assertEquals(-width, tabSlideEnterOffset(shift = -1, width = width))
        assertEquals(width, tabSlideExitOffset(shift = -1, width = width))
    }

    @Test
    fun `进出位移等长反向 - 两页是一起动的`() {
        // 一页进、一页出，位移必须严格互补；谁多走一像素，中间那条缝就会忽宽忽窄
        for (shift in listOf(-1, 1)) {
            assertEquals(0, tabSlideEnterOffset(shift, width) + tabSlideExitOffset(shift, width))
        }
    }

    @Test
    fun `任何一帧两页都拼成一条 - 中间既不叠也不空`() {
        // p = 动画进度：退出页左边缘 = p * exit，进入页左边缘 = (1 - p) * enter。
        // "拼成一条"就是：退出页右边缘(exit 左边缘 + 一屏宽) 正好等于进入页左边缘。
        for (shift in listOf(-1, 1)) {
            for (i in 0..10) {
                val p = i / 10f
                val exitLeft = (p * tabSlideExitOffset(shift, width)).roundToInt()
                val enterLeft = ((1f - p) * tabSlideEnterOffset(shift, width)).roundToInt()
                val gap = enterLeft - (exitLeft + width * shift)
                assertTrue(
                    "shift=$shift p=$p 两页之间有 $gap px 的错位",
                    kotlin.math.abs(gap) <= 1,
                )
            }
        }
    }

    // ---- 时长 ----

    @Test
    fun `横滑时长比 base 长、不超过 slow`() {
        // 标准 / Plasma / HyperOS 三档（见 ThemeStyle.kt），全部要落在区间里
        val specs = listOf(
            MotionSpec(fast = 120, base = 200, slow = 320, springy = false),
            MotionSpec(fast = 150, base = 250, slow = 420, springy = false),
            MotionSpec(fast = 200, base = 350, slow = 500, springy = false),
        )
        for (m in specs) {
            val d = tabSlideDurationMs(m)
            assertTrue("${m.base}/${m.slow} -> $d 没有比 base 长", d > m.base)
            assertTrue("${m.base}/${m.slow} -> $d 超过了 slow", d <= m.slow)
        }
    }

    // ---- 悬浮底栏的胶囊 ----

    @Test
    fun `胶囊位置夹在四栏之内`() {
        assertEquals(0f, clampPill(-1.5f, 4))
        assertEquals(3f, clampPill(9f, 4))
        assertEquals(2.5f, clampPill(2.5f, 4))
        // 只有一栏 / 空列表时不该出现"第 -1 栏"
        assertEquals(0f, clampPill(0.4f, 1))
        assertEquals(0f, clampPill(0.4f, 0))
    }

    @Test
    fun `松手吸附到最近那一栏`() {
        assertEquals(0, nearestTab(0.2f, 4))
        assertEquals(1, nearestTab(0.6f, 4))
        assertEquals(2, nearestTab(2.49f, 4))
        assertEquals(3, nearestTab(2.6f, 4))
        // 越界也不会吸到画外
        assertEquals(0, nearestTab(-3f, 4))
        assertEquals(3, nearestTab(9f, 4))
        assertEquals(0, nearestTab(2f, 0))
    }

    @Test
    fun `手指位置换算成栏号`() {
        val tab = 270f
        assertEquals(0, tabIndexAt(0f, tab, 4))
        assertEquals(0, tabIndexAt(269f, tab, 4))
        assertEquals(1, tabIndexAt(270f, tab, 4))
        assertEquals(3, tabIndexAt(1080f, tab, 4))
        // 量不到宽度时退回第一栏（而不是除零或 -1）
        assertEquals(0, tabIndexAt(500f, 0f, 4))
    }

    @Test
    fun `染色权重随胶囊连续交接`() {
        // 停在某一栏：那一栏满权重，邻栏没有
        assertEquals(1f, tabSelectionWeight(1f, 1), 1e-6f)
        assertEquals(0f, tabSelectionWeight(1f, 0), 1e-6f)
        assertEquals(0f, tabSelectionWeight(1f, 2), 1e-6f)
        // 走到两栏中间：两栏各一半 —— 这是"连续"最直观的一处
        assertEquals(0.5f, tabSelectionWeight(1.5f, 1), 1e-6f)
        assertEquals(0.5f, tabSelectionWeight(1.5f, 2), 1e-6f)
        // 权重永远在 0..1（隔了一栏以上就是 0，不会出现负值把颜色算反）
        for (i in 0..4) {
            for (p in listOf(0f, 0.5f, 1.5f, 2.5f, 3.6f, 4f)) {
                val w = tabSelectionWeight(p, i)
                assertTrue("pill=$p index=$i weight=$w", w in 0f..1f)
            }
        }
    }
}
