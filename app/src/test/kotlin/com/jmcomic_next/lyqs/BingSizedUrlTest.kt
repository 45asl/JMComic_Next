package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.data.wallpaper.bingSizedUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Bing 地址的尺寸改写。
 *
 * 实测：把 `_1920x1080.jpg` 改成 `_1080x1920.jpg` 之后，Bing 真的返回 1080×1920 的竖图
 * （不是把横图拉长）—— 手机竖屏用横图会被裁掉两侧一大块，所以这一步是有意义的，
 * 不是「看起来更讲究」而已。
 */
class BingSizedUrlTest {

    private val landscape = "https://www.bing.com/th?id=OHR.ElGolfo_ZH-CN8329995759_1920x1080.jpg"

    @Test
    fun `portrait screens ask for a portrait crop`() {
        assertEquals(
            "https://www.bing.com/th?id=OHR.ElGolfo_ZH-CN8329995759_1080x1920.jpg",
            bingSizedUrl(landscape, portrait = true),
        )
    }

    @Test
    fun `landscape screens keep a landscape crop`() {
        assertEquals(
            "https://www.bing.com/th?id=OHR.ElGolfo_ZH-CN8329995759_1920x1080.jpg",
            bingSizedUrl(landscape, portrait = false),
        )
    }

    @Test
    fun `addresses without a size segment are left alone`() {
        // 这是别人的地址：猜错形状就得整张图加载失败，所以宁可不改
        val other = "https://t.alcy.cc/ycy"
        assertEquals(other, bingSizedUrl(other, portrait = true))
        assertEquals(
            "https://example.com/a.jpg",
            bingSizedUrl("https://example.com/a.jpg", portrait = true),
        )
    }

    @Test
    fun `only the first size segment is rewritten`() {
        val weird = "https://x/th?id=a_1920x1080.jpg&u=b_640x480.jpg"
        assertEquals(
            "https://x/th?id=a_1080x1920.jpg&u=b_640x480.jpg",
            bingSizedUrl(weird, portrait = true),
        )
    }
}
