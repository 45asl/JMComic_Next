package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.data.crypto.JmCrypto
import com.jmcomic_next.lyqs.data.image.ImageUnscramble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 切片还原的几何与像素搬运（2.0.0）。
 *
 * 这段算法从 Android 侧搬进跨平台模块后才有条件这样测：它现在是纯数字与数组运算，
 * 不必构造 Bitmap。要验证的性质是「条带不重不漏地铺满整页」—— 这是最容易写错、
 * 又最不容易在真机上被发现的地方（错位几个像素往往要盯着一页图看才察觉）。
 */
class ImageUnscrambleTest {

    /** 几何：每条带的源区间与目标区间都必须恰好覆盖整页一次。 */
    @Test
    fun `bands tile the page exactly once`() {
        for (h in listOf(120, 121, 128, 300, 1000, 1023)) {
            for (num in 2..12) {
                val bands = ImageUnscramble.bands(800, h, num)
                if (bands.isEmpty()) continue

                // 源行覆盖：把每条带的源区间摊平，应当正好是 0..h-1 且不重复
                val srcRows = IntArray(h)
                var srcTotal = 0
                for (b in bands) {
                    for (y in b.srcY until b.srcY + b.height) srcRows[y]++
                    srcTotal += b.height
                }
                assertEquals("源行总数应等于页高（h=$h num=$num）", h, srcTotal)
                assertTrue("源行不应被重复取用（h=$h num=$num）", srcRows.all { it == 1 })

                // 目标行覆盖：同样应当不重不漏
                val dstRows = IntArray(h)
                for (b in bands) {
                    assertTrue("目标区间不应越界（h=$h num=$num）", b.dstY >= 0 && b.dstY + b.height <= h)
                    for (y in b.dstY until b.dstY + b.height) dstRows[y]++
                }
                assertTrue("目标行不应重叠或留空（h=$h num=$num）", dstRows.all { it == 1 })
            }
        }
    }

    /** 像素搬运与几何必须一致：输出第 dstY 行应当等于输入第 srcY 行。 */
    @Test
    fun `pixels follow the band geometry`() {
        // 找一个确实需要还原的 aid/page 组合（份数由 md5 推出，不在测试里硬编码）
        var aid = 100000
        var page = "1"
        while (JmCrypto.sliceCount(aid, page) <= 1 && aid < 100200) aid++
        val num = JmCrypto.sliceCount(aid, page)
        assertTrue("应当找到一个需要还原的组合，实际 num=$num", num > 1)

        val w = 4
        val h = 96
        // 每行的每个像素都填该行的行号，便于逐行核对
        val src = IntArray(w * h) { i -> (i / w) or 0xFF000000.toInt() }
        val out = ImageUnscramble.unscramble(src, w, h, aid, page)

        for (b in ImageUnscramble.bands(w, h, num)) {
            for (dy in 0 until b.height) {
                val srcRow = b.srcY + dy
                val dstRow = b.dstY + dy
                for (x in 0 until w) {
                    assertEquals(
                        "dst 行 $dstRow 应来自 src 行 $srcRow",
                        (srcRow or 0xFF000000.toInt()),
                        out[dstRow * w + x],
                    )
                }
            }
        }
    }

    /** 不需要还原时应当原样返回同一个数组（调用方据此避免多余的复制）。 */
    @Test
    fun `no scramble returns the very same array`() {
        // num <= 1 的路径：几何为空
        assertTrue(ImageUnscramble.bands(100, 100, 1).isEmpty())
        assertTrue(ImageUnscramble.bands(100, 100, 0).isEmpty())
        assertTrue(ImageUnscramble.bands(0, 100, 4).isEmpty())
        assertTrue(ImageUnscramble.bands(100, 2, 4).isEmpty())

        // 走到"无需还原"的真实路径是**几何退化**，不是份数 <= 1：
        // aid < 268850 时 key 是 md5 末位的 ASCII 码，when 里全不匹配，一律落到 else -> 10。
        // （这条是我一开始写错的地方 —— sliceCount 并不是"要不要切"的开关，
        //  ​那件事由 JmCrypto.needsUnscramble 判断。）
        val aid = 1
        val page = "1"
        val num = JmCrypto.sliceCount(aid, page)
        assertTrue("页高小于份数时应无需还原", ImageUnscramble.bands(100, num - 1, num).isEmpty())

        val px = IntArray(100 * (num - 1))
        assertSame(px, ImageUnscramble.unscramble(px, 100, num - 1, aid, page))
    }

    /** 页高能被份数整除时不应有 remainder 分支的特殊行为。 */
    @Test
    fun `even division produces equal bands`() {
        val bands = ImageUnscramble.bands(10, 256, 4)
        assertEquals(4, bands.size)
        assertTrue("整除时每条带高度应相同", bands.all { it.height == 64 })
        val dst = bands.map { it.dstY }
        assertArrayEquals(intArrayOf(0, 64, 128, 192), dst.toIntArray())
    }
}
