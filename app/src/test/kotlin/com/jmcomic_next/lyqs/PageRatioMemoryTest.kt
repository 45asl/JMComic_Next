package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.ui.screens.reader.PageRatioMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 页面比例记忆。
 *
 * 它存在的唯一理由是**让占位高度尽量等于真实高度** —— 猜错一次，列表就在用户
 * 正在读的位置跳一下。所以测试盯的是两件事：记下来的能取回来（含按作品的兜底），
 * 以及**不可信的尺寸不能进去**（解码未完成时会给出 0 或未就绪的值，放进去只会更糟）。
 */
class PageRatioMemoryTest {

    @Test
    fun `记住的页面比例能按 url 与按作品取回`() {
        val aid = 8801
        val url = "https://cdn.example/a/00001.webp"
        PageRatioMemory.remember(aid = aid, url = url, ratio = 0.6f)

        assertEquals(0.6f, PageRatioMemory.ratioFor(url)!!, 0.0001f)
        // 按作品的兜底同样要更新：后面还没加载的页靠它占位
        assertEquals(0.6f, PageRatioMemory.albumRatio(aid)!!, 0.0001f)
    }

    @Test
    fun `不可信的尺寸会被丢掉`() {
        // 这些都是在解码未完成 / 占位阶段可能拿到的值。放进去的话，
        // 之后的页会用一个错得离谱的高度占位 —— 比不记还糟。
        val bad = listOf(0f, -1f, 99f, Float.NaN, Float.POSITIVE_INFINITY)
        bad.forEachIndexed { i, ratio ->
            val url = "https://cdn.example/bad/$i.webp"
            PageRatioMemory.remember(aid = 1, url = url, ratio = ratio)
            assertNull("比例 $ratio 不该被记住", PageRatioMemory.ratioFor(url))
        }
    }

    @Test
    fun `没量过的页返回 null 而不是瞎猜`() {
        // 返回 null 才会让调用方退回到「本作品最近实测的比例」，最后才用兜底常数。
        // 这里若返回一个假值，那条回退链就断了。
        assertNull(PageRatioMemory.ratioFor("https://cdn.example/never-seen.webp"))
        assertNull(PageRatioMemory.albumRatio(999999))
    }
}
