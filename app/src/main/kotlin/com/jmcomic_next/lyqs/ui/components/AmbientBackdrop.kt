package com.jmcomic_next.lyqs.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.jmcomic_next.lyqs.ui.theme.JmTheme

/**
 * 环境渐变底 —— 整个应用的 Acrylic 采样底。
 *
 * 这里对应博客 `--wallpaper` 令牌的**渐变网格**部分，
 * 但**刻意不实现壁纸系统**：没有图源、没有壁纸选择器、没有第三方接口。
 * 博客里那套可调壁纸（五种来源模式）在本应用中不适用，理由有二：
 *
 *  1. 阅读类应用的可读性优先，背景强度必须稳定可预期；
 *  2. 毛玻璃只需要一个「有层次、有色相变化」的底就成立，
 *     渐变网格已经足够，引入图片反而增加解码与内存开销。
 *
 * 博客的 `--wallpaper` 由四层构成（三层径向 + 一层线性），深浅色各自一组色值，
 * 这里按同样的层序复现，色相取自 [com.jmcomic_next.lyqs.ui.theme.JmPalette]。
 */
@Composable
fun AmbientBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = JmTheme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height

                // 1. 底层线性渐变（CSS: linear-gradient(165deg, ...)）
                val base = Brush.linearGradient(
                    colors = c.backdrop,
                    start = Offset(0f, 0f),
                    end = Offset(w * 0.35f, h),
                )

                // 2. 左上：偏蓝的主光斑（CSS: radial-gradient(1100px 720px at 12% -8%)）
                val glowA = Brush.radialGradient(
                    colors = listOf(c.accent.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(w * 0.12f, -h * 0.08f),
                    radius = maxOf(w, h) * 0.75f,
                )

                // 3. 右上：偏紫的补光斑
                val glowB = Brush.radialGradient(
                    colors = listOf(c.tintWarm.copy(alpha = 0.34f), Color.Transparent),
                    center = Offset(w * 0.88f, h * 0.04f),
                    radius = maxOf(w, h) * 0.68f,
                )

                // 4. 下方：偏青的收尾光斑
                val glowC = Brush.radialGradient(
                    colors = listOf(c.tintCool.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(w * 0.62f, h * 1.08f),
                    radius = maxOf(w, h) * 0.72f,
                )

                onDrawWithContent {
                    drawRect(brush = base)
                    drawRect(brush = glowA)
                    drawRect(brush = glowB)
                    drawRect(brush = glowC)
                    drawContent()
                }
            },
        content = content,
    )
}
