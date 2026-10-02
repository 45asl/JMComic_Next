package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.ui.theme.cornerInsetRatio
import com.jmcomic_next.lyqs.ui.theme.superellipseUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow

/**
 * 连续圆角的几何。
 *
 * 为什么不去量截图：预览卡里的迷你场景只有 16px 半径，抗锯齿的误差跟要分辨的差别同量级，
 * 量出来是噪声（我试过，四个风格给出的比值是 0.875 / 1.000 / 0.667 / 0.556 —— 全是噪声）。
 * 而「角到底是不是连续的」本质是一个**数学事实**，直接测数学即可：
 *
 *  - 圆弧（n=2）：对角内缩 0.293r
 *  - 超椭圆（n=5）：对角内缩 0.129r
 *
 * 差 2.3 倍，肉眼与像素都不在话下，用断言钉死最省事。
 */
class SquircleShapeTest {

    @Test
    fun `circle inset ratio matches the analytic value`() {
        // n=2 时超椭圆就是圆，应当回到 1 - 1/√2
        assertEquals(1f - 1f / kotlin.math.sqrt(2f), cornerInsetRatio(2f), 0.0005f)
    }

    @Test
    fun `squircle corners are fuller than circular ones`() {
        val circle = cornerInsetRatio(2f)
        val squircle = cornerInsetRatio(5f)
        assertEquals(0.129f, squircle, 0.002f)
        assertTrue("连续圆角的对角内缩应当明显更小", squircle < circle * 0.5f)
    }

    @Test
    fun `larger exponent makes the corner fuller`() {
        // 指数越大越方：内缩单调变小
        val ratios = listOf(2f, 3f, 5f, 8f).map { cornerInsetRatio(it) }
        assertEquals(ratios.sortedDescending(), ratios)
    }

    @Test
    fun `unit points stay on the superellipse`() {
        // 抽查几个角度：|x|^n + |y|^n 应当恒等于 1
        val n = 5f
        listOf(0f, 0.3f, PI.toFloat() / 4, 1.1f, PI.toFloat() / 2).forEach { t ->
            val (x, y) = superellipseUnit(t, n)
            val v = x.toDouble().pow(n.toDouble()).toFloat() + y.toDouble().pow(n.toDouble()).toFloat()
            assertEquals("θ=$t 上的点应当落在超椭圆上", 1f, v, 0.002f)
        }
    }

}
