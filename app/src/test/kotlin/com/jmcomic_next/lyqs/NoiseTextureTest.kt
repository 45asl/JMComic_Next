package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.ui.components.NOISE_SIZE
import com.jmcomic_next.lyqs.ui.components.noisePixels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 颗粒纹理的确定性（1.6.0）。
 *
 * 这条测试的意义不是"像素对不对"，而是**共享是否安全**：进程内只留一份纹理，
 * 前提就是它永远不变。如果哪天有人往里面加了随机数或时间，这个测试会立刻失败 ——
 * 否则那种改动会表现为"玻璃的颗粒在每次重组时抖动"，很难查。
 */
class NoiseTextureTest {

    @Test
    fun `pixels are deterministic`() {
        val a = noisePixels()
        val b = noisePixels()
        assertTrue("两次生成的纹理必须完全一致", a.contentEquals(b))
        assertEquals(NOISE_SIZE * NOISE_SIZE, a.size)
    }

    @Test
    fun `texture is not flat`() {
        // 全同色的纹理等于没有颗粒：真出现这种情况，玻璃的质感就悄悄没了
        assertTrue("颗粒不该是单色", noisePixels().toSet().size > 8)
    }

    @Test
    fun `alpha is the fixed overlay value`() {
        // 注释里写明 alpha 必须是 0x80；改成别处会让颗粒幅度或均值偏移
        val alpha = noisePixels().first() ushr 24
        assertEquals(0x80, alpha)
        assertTrue("所有像素的 alpha 一致", noisePixels().all { it ushr 24 == 0x80 })
    }
}
