package com.jmcomic_next.lyqs.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * 连续圆角（squircle / 平滑圆角）。
 *
 * **为什么需要它。** 普通 `RoundedCornerShape` 的角是四分之一圆弧：直线与圆弧在切点处
 * 曲率**突变**，肉眼能看出那一下「折」。HyperOS / MIUI 那边管这个叫平滑圆角
 * （小米自家文档与社区里都把它当成一个单独的图标/卡片概念，其组件库对应
 * `squircleSurface(...)`），iOS 也是同一路子。
 *
 * 做法是超椭圆 `|x/a|^n + |y/b|^n = 1`（n≈5）而不是圆：角上更「饱满」，
 * 45° 方向上离角心的距离约为半径的 **0.86 倍**，而圆弧只有 0.707 倍 ——
 * 这个差别既能看出来，也**量得出来**（[scripts/px_probe.py] 会量它）。
 *
 * 只给 Miuix 风格用。Material 与 Windows 那两套的圆角就是圆角，
 * 给它们套上平滑圆角反而不像了。
 */
class SquircleShape(
    private val radius: Dp,
    /** 角上采样段数。12 段在 320dpi 下已经看不出折线，再密只是白算。 */
    private val steps: Int = 12,
    /** 超椭圆指数：越大越方，5 接近 iOS 的观感。 */
    private val exponent: Float = 5f,
) : Shape {

    /** 供测试与文档引用：这个形状用的指数。 */
    val exponentUsed: Float get() = exponent

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = with(density) { radius.toPx() }.coerceAtMost(min(size.width, size.height) / 2f)
        val path = Path()
        if (r <= 0f) {
            path.addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
            return Outline.Generic(path)
        }

        fun point(theta: Float, cx: Float, cy: Float, sx: Float, sy: Float): Offset {
            val (c, s) = superellipseUnit(theta, exponent)
            return Offset(cx + sx * r * c, cy + sy * r * s)
        }

        fun quarter(cx: Float, cy: Float, sx: Float, sy: Float) {
            for (i in 1..steps) {
                val t = (i.toFloat() / steps) * (PI / 2).toFloat()
                val p = point(t, cx, cy, sx, sy)
                path.lineTo(p.x, p.y)
            }
        }

        // 顺时针：上边 → 右上角 → 右边 → 右下角 → 下边 → 左下角 → 左边 → 左上角
        path.moveTo(r, 0f)
        path.lineTo(size.width - r, 0f)
        quarter(size.width - r, r, 1f, -1f)
        path.lineTo(size.width, size.height - r)
        quarter(size.width - r, size.height - r, 1f, 1f)
        path.lineTo(r, size.height)
        quarter(r, size.height - r, -1f, 1f)
        path.lineTo(0f, r)
        quarter(r, r, -1f, -1f)
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * 超椭圆上一点的单位坐标（半径 1）：`x = |cosθ|^(2/n)`、`y = |sinθ|^(2/n)`。
 *
 * 抽成纯函数是为了**能测**：45° 方向上 `x = 0.7071^(2/n)`，
 * n=5 时约 0.871，而圆（n=2）是 0.707 —— 这就是「连续圆角更饱满」的全部内容，
 * 也是单元测试要钉住的那个数。靠对 16px 的角落截图取色去分辨它不现实。
 */
internal fun superellipseUnit(theta: Float, exponent: Float): Pair<Float, Float> {
    val c = abs(cos(theta)).pow(2f / exponent)
    val s = abs(sin(theta)).pow(2f / exponent)
    return c to s
}

/**
 * 角上「对角内缩」与「顶边内缩」的比值：
 *
 *  - 圆弧：`1 - 1/√2 ≈ 0.293`（半径 r 的角，对角线方向的内缩是 0.293r）
 *  - 超椭圆 n=5：`1 - 0.7071^(2/5) ≈ 0.129`
 *
 * 差别 2.3 倍。数值越小说明角越「饱满」（越接近连续圆角）。
 */
internal fun cornerInsetRatio(exponent: Float): Float {
    val (c, _) = superellipseUnit((PI / 4).toFloat(), exponent)
    return 1f - c
}

/**
 * 取当前风格该用的圆角形状。
 *
 * 界面里所有 `RoundedCornerShape(Radius.xx)` 都换成它，于是 Miuix 一处不落地变成连续圆角，
 * 另外三套保持圆弧 —— 页面依然不需要知道自己在哪套风格下，判断只发生在这里。
 *
 * **胶囊形（[Radius.pill]）不要走这里**：胶囊就是全圆角，平滑它没有意义。
 */
@Composable
@ReadOnlyComposable
fun jmShape(radius: Dp): Shape =
    if (JmTheme.spec.surface.craft == SurfaceCraft.Card) {
        SquircleShape(radius)
    } else {
        RoundedCornerShape(radius)
    }
