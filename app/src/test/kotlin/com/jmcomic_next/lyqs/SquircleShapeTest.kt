package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.ui.theme.cornerInsetRatio
import com.jmcomic_next.lyqs.ui.theme.superellipseRoundRect
import com.jmcomic_next.lyqs.ui.theme.superellipseUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow

/** 一个角的角心与「朝外」的方向（`sx`：右为 +1；`sy`：下为 +1）。 */
private data class Corner(val cx: Float, val cy: Float, val sx: Float, val sy: Float)

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
 *
 * **下面这一组还补上了「四个角拼起来对不对」。** 这件事原来没人测，代价是一次真 bug：
 * 右上与左下两个角被反向扫过，轮廓自交，Miuix 主题的这两个角被填掉一块。
 * 注意这个 bug **上面那几条数学测试一个都拦不住** —— 反向扫出来的点依然落在同一条
 * 超椭圆上。要拦住它，只能对拼装后的轮廓本身做断言。
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

    // ---- 轮廓的拼装（上面那几条测不到的部分） ----

    @Test
    fun `outline is convex, so no corner is scanned backwards`() {
        // 这是「Miuix 圆角被切掉一块」那个 bug 的回归测试。
        // 反向扫一个角会让轮廓自交，几何上的表现就是：连续三点的叉积出现反号。
        // 实测修复前 53 个叉积里有 24 个反号；修复后全部同号（只剩一个约 0 的退化项）。
        val pts = superellipseRoundRect(200f, 120f, 16f)
        val crosses = pts.indices.map { i ->
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            val c = pts[(i + 2) % pts.size]
            (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        }.filter { abs(it) > 1e-3f }

        assertTrue("取到的叉积太少（${crosses.size}），这条测试就没意义了", crosses.size > 40)
        assertTrue(
            "轮廓必须整体朝同一方向转；出现反号说明有角被反着扫了",
            crosses.all { it > 0f } || crosses.all { it < 0f },
        )
    }

    @Test
    fun `outline closes and stays inside the box`() {
        val w = 200f
        val h = 120f
        val pts = superellipseRoundRect(w, h, 16f)

        pts.forEach { p ->
            assertTrue("x 越界：${p.x}", p.x >= -0.01f && p.x <= w + 0.01f)
            assertTrue("y 越界：${p.y}", p.y >= -0.01f && p.y <= h + 0.01f)
        }
        // 首尾要能接上：Path.close() 只会补这一段
        val gap = hypot(pts.last().x - pts.first().x, pts.last().y - pts.first().y)
        assertTrue("闭合处跨度应当接近 0，实际 $gap", gap < 0.5f)
    }

    @Test
    fun `all four corners bulge like a superellipse, not like a circular arc`() {
        val w = 200f
        val h = 120f
        val r = 16f
        val pts = superellipseRoundRect(w, h, r)
        val corners = listOf(
            Corner(w - r, r, 1f, -1f),
            Corner(w - r, h - r, 1f, 1f),
            Corner(r, h - r, -1f, 1f),
            Corner(r, r, -1f, -1f),
        )

        corners.forEach { (cx, cy, sx, sy) ->
            // 角上的点：在朝外的象限里、且离角心不超过 1.3r（超椭圆 45° 处最远，约 1.23r）
            val reach = pts
                .filter { p ->
                    val u = sx * (p.x - cx)
                    val v = sy * (p.y - cy)
                    u >= -0.01f && v >= -0.01f && hypot(u, v) <= r * 1.3f
                }
                .maxOf { p -> sx * (p.x - cx) + sy * (p.y - cy) }

            // 45° 方向：超椭圆 n=5 给 2·0.7071^(2/5) = 1.741r；四分之一圆弧只有 2·0.7071 = 1.414r。
            // 四个角都要满足 —— 只测一个角的话，另外三个角被画反了也发现不了。
            assertEquals("角心($cx,$cy) 的对角伸展不像超椭圆", 1.7412f * r, reach, 0.05f * r)
            assertTrue("角必须比圆弧饱满（>1.5r），实际 $reach", reach > 1.5f * r)
        }
    }

    @Test
    fun `degenerate radii fall back to a plain rectangle`() {
        val w = 200f
        val h = 120f
        // r=0：四个角点，就是一个矩形
        val zero = superellipseRoundRect(w, h, 0f)
        assertEquals(listOf(0f, 0f, w, 0f, w, h, 0f, h), zero.flatMap { listOf(it.x, it.y) })

        // r 超过短边一半：按短边一半收敛，不能把轮廓画反
        val clamped = superellipseRoundRect(w, 40f, 500f)
        clamped.forEach { p ->
            assertTrue("收敛后仍越界：${p.x},${p.y}", p.x >= -0.01f && p.x <= w + 0.01f)
            assertTrue("收敛后仍越界：${p.x},${p.y}", p.y >= -0.01f && p.y <= 40f + 0.01f)
        }
        val gap = hypot(clamped.last().x - clamped.first().x, clamped.last().y - clamped.first().y)
        assertTrue("收敛后首尾接不上，跨度 $gap", gap < 0.5f)
    }
}
