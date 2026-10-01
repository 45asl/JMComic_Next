package com.jmcomic_next.lyqs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jmcomic_next.lyqs.ui.theme.Elevation
import com.jmcomic_next.lyqs.ui.theme.Glass
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Radius
import com.jmcomic_next.lyqs.ui.theme.Sizing

/**
 * 玻璃表面的层级。
 *
 * 对应博客的三档表面令牌 —— 数字越大越靠近用户、越不透明、投影越重：
 * [Card] → `--surface-1`，[Raised] → `--surface-2`，[Flyout] → `--surface-3`。
 */
enum class GlassLevel { Card, Raised, Flyout }

/**
 * Acrylic / Mica 玻璃表面。
 *
 * 博客用 `backdrop-filter: blur(40px) saturate(165%)` 采样背后的环境；
 * Android 的 Compose 没有等价能力（无法读取「已绘制内容」再作模糊），
 * 因此这里用四层叠加来还原观感：
 *
 *  1. 半透明填充（[GlassLevel] 决定不透明度）
 *  2. 可选 MIUI 橙→蓝渐变薄层（[tinted]，对应博客的 --appbar-tint）
 *  3. 发丝外描边 `--stroke`
 *  4. 上缘高光 `--stroke-inner`，制造玻璃厚度感
 *
 * 环境渐变底由 [com.jmcomic_next.lyqs.ui.components.AmbientBackdrop] 铺在最底层，
 * 半透明填充叠在它之上就得到 Mica 的观感 —— 这也是本设计不需要壁纸的原因。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.Card,
    shape: Shape = RoundedCornerShape(Radius.lg),
    tinted: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = JmTheme.colors

    val fill = when (level) {
        GlassLevel.Card -> c.surface1
        GlassLevel.Raised -> c.surface2
        GlassLevel.Flyout -> c.surface3
    }
    val elevation = when (level) {
        GlassLevel.Card -> Elevation.sm
        GlassLevel.Raised -> Elevation.card
        GlassLevel.Flyout -> Elevation.flyout
    }

    // MIUI 橙→蓝薄层，CSS 的 120deg 用对角线性渐变近似
    val tintBrush = Brush.linearGradient(
        colors = listOf(c.tintWarm, c.tintCool),
        start = Offset.Zero,
        end = Offset.Infinite,
    )

    Box(
        modifier = modifier
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(fill)
            .then(
                if (tinted) {
                    Modifier.drawWithContent {
                        drawContent()
                        drawRect(brush = tintBrush)
                    }
                } else {
                    Modifier
                }
            )
            .border(Sizing.hairline, c.stroke, shape)
            .drawWithContent {
                // 上缘高光：一条很短的竖向渐变，只留 3dp 高度，模拟玻璃的厚度反光
                drawContent()
                val h = Glass.innerHighlight.toPx() * 3f
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(c.strokeInner, Color.Transparent),
                        startY = 0f,
                        endY = h,
                    ),
                    size = Size(size.width, h),
                )
            }
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            ),
        content = content,
    )
}

/**
 * 左侧强调条。博客用 3px 竖条标出「当前项」，
 * 抽成独立修饰符供导航项、列表选中态复用。
 */
fun Modifier.accentBar(color: Color, show: Boolean = true): Modifier = drawWithContent {
    drawContent()
    if (!show) return@drawWithContent
    val w = Sizing.accentBar.toPx()
    val h = size.height * 0.56f
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, (size.height - h) / 2f),
        size = Size(w, h),
        cornerRadius = CornerRadius(w / 2f, w / 2f),
    )
}

/** 圆形玻璃表面，用于头像、图标按钮等小尺寸场景。 */
@Composable
fun GlassCircle(
    modifier: Modifier = Modifier,
    diameter: Dp = 40.dp,
    level: GlassLevel = GlassLevel.Raised,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier.size(diameter),
        level = level,
        shape = RoundedCornerShape(percent = 50),
        onClick = onClick,
        content = content,
    )
}
