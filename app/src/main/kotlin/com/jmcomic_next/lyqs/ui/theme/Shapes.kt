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
        val r = with(density) { radius.toPx() }
        val points = superellipseRoundRect(size.width, size.height, r, steps, exponent)
        val path = Path()
        path.moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * 超椭圆圆角矩形的轮廓点，顺时针一圈。
 *
 * **为什么把它从 [SquircleShape] 里抽出来。** 原来的版本直接在 `createOutline` 里
 * 一段段往 `Path` 上画，于是「四个角拼起来这条线对不对」没有任何东西检查它。
 * 实际的后果是：右上与左下两个角被**反向**扫了一遍（从角的出口扫回入口），轮廓在那两处自交。
 * Compose 的 `Path` 默认是**非零环绕**填充，反向的那一圈把绕数抵消掉，所以这两个角
 * 不是多出一块尖刺，而是**少填一块** —— 栅格化实测：整形状差 9.46% 面积，
 * 右上少填 3971px、左下 4056px，另外两个角各只差约 20px。
 *
 * 而当时 `superellipseUnit` 的测试全绿：它测的是「点在不在超椭圆上」，
 * 反向扫出来的点**依然落在同一条超椭圆上** —— 错的是这些点的**连接顺序**，不是位置。
 * 点列变成返回值之后，凸性、闭合性、不越界都成了可以直接断言的事实（见 `SquircleShapeTest`）。
 *
 * 采样密度本身没问题：12 段与理想曲线的最大偏离是 0.0018·r（r=44px 时约 0.08px），
 * 所以不用靠加密采样来遮这个 bug。
 */
internal fun superellipseRoundRect(
    width: Float,
    height: Float,
    radius: Float,
    steps: Int = 12,
    exponent: Float = 5f,
): List<Offset> {
    val r = radius.coerceIn(0f, min(width, height) / 2f)
    if (r <= 0f) {
        return listOf(
            Offset(0f, 0f),
            Offset(width, 0f),
            Offset(width, height),
            Offset(0f, height),
        )
    }

    val points = ArrayList<Offset>(4 * steps + 5)

    /**
     * 走一个角：从 [entryTurns] 扫到 [exitTurns]，两者都以 π/2 为单位。
     *
     * `θ=0` 落在水平轴上（`sx=+1` 是右侧、`-1` 是左侧），`θ=π/2` 落在竖直轴上
     * （`sy=+1` 是下方、`-1` 是上方）。**「从哪个端点进入」决定了扫描方向**，
     * 写反了角就反向自交 —— 这正是修掉的那个 bug，所以这里把两个端点显式写出来，
     * 不再靠调用方“记得”该正着扫还是反着扫。
     */
    fun corner(cx: Float, cy: Float, sx: Float, sy: Float, entryTurns: Int, exitTurns: Int) {
        for (i in 1..steps) {
            val f = i.toFloat() / steps
            val turns = entryTurns + (exitTurns - entryTurns) * f
            val (c, s) = superellipseUnit(turns * (PI / 2).toFloat(), exponent)
            points += Offset(cx + sx * r * c, cy + sy * r * s)
        }
    }

    // 上边 → 右上 → 右边 → 右下 → 下边 → 左下 → 左边 → 左上
    points += Offset(r, 0f)
    points += Offset(width - r, 0f)
    // 右上角：从「上」进（θ=π/2），从「右」出（θ=0）
    corner(width - r, r, 1f, -1f, entryTurns = 1, exitTurns = 0)
    points += Offset(width, height - r)
    // 右下角：从「右」进（θ=0），从「下」出（θ=π/2）
    corner(width - r, height - r, 1f, 1f, entryTurns = 0, exitTurns = 1)
    points += Offset(r, height)
    // 左下角：从「下」进（θ=π/2），从「左」出（θ=0）
    corner(r, height - r, -1f, 1f, entryTurns = 1, exitTurns = 0)
    points += Offset(0f, r)
    // 左上角：从「左」进（θ=0），从「上」出（θ=π/2）
    corner(r, r, -1f, -1f, entryTurns = 0, exitTurns = 1)
    return points
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
