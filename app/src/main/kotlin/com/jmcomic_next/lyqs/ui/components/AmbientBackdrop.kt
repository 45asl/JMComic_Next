package com.jmcomic_next.lyqs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jmcomic_next.lyqs.ui.LocalUiOptions
import com.jmcomic_next.lyqs.ui.LocalWallpaper
import com.jmcomic_next.lyqs.ui.theme.JmTheme

/**
 * 环境底 —— 整个应用的采样底，两层的组合：
 *
 *  1. **渐变网格**（永远有）：对应博客 `--wallpaper` 令牌里那四层
 *     （三层径向 + 一层线性），深浅色各一组色值，这里按同样的层序复现。
 *     实心风格（Miuix / Material）的配色把渐变换成了平色，于是这一层自动退化成纯色底。
 *  2. **壁纸图片**（可选）：只有用户自己开了才画，见 [com.jmcomic_next.lyqs.data.wallpaper.WallpaperMode]。
 *
 * 关于「为什么默认没有壁纸」：博客是网页，背景图不影响阅读；本应用是阅读器，
 * 所以默认档是纯渐变、**不发任何网络请求**，想换背景的人自己去设置里开。
 *
 * 壁纸之上的遮罩由两部分相加：用户调的压暗值（对应博客的 `--wallpaper-dim`）
 * 与当前风格的 [com.jmcomic_next.lyqs.ui.theme.JmSpec.wallpaperScrim]。
 * 后者是必要的：风格不同，玻璃透出来的程度差很多，Translucent 那种重度透明
 * 如果只按博客默认的 0.12 压暗，文字压在花壁纸上根本读不了。
 */
@Composable
fun AmbientBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = JmTheme.colors
    val spec = JmTheme.spec
    val wall = LocalWallpaper.current
    val options = LocalUiOptions.current

    // 底的高斯模糊来自两处，取较大者：风格自己的 backdropBlur（Acrylic 40dp /
    // Translucent 64dp / FlatBlur 56dp），以及用户在壁纸设置里手动调的模糊。
    //
    // 这一行是 1.3.3 修的一个真问题：风格的 `blur` 令牌以前**没有任何地方读它**，
    // 声明了 40/64dp 却是死值 —— 所谓「玻璃」其实一点模糊都没有。
    val effectiveBlur = maxOf(spec.surface.backdropBlur, wall.blur.dp)
    // 同一个令牌的另一半：Acrylic 在模糊之后还会提饱和度，以前也没实现
    val saturate = spec.surface.backdropSaturate

    // 莫奈取色套用在模糊上（可选）：用动态取色派生的三个色相给「底」上色。
    // 只把强调色换成系统色的话，背景仍是我们自己定的渐变，玻璃糊出来的颜色与壁纸无关；
    // 拿不到动态取色（Android 12 以下或没开）时 monetTints 为空，这个开关自动不生效。
    val monet = if (options.monetBlur) c.monetTints else emptyList()

    Box(modifier = modifier.fillMaxSize()) {
        // 1. 渐变网格（或实心风格的纯色底）
        Box(modifier = Modifier.fillMaxSize().ambientBase())

        // 2. 壁纸图片
        if (wall.showsImage) {
            AsyncImage(
                model = wall.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Acrylic 的 saturate()：模糊把相邻像素平均掉之后颜色会发灰，
                // 提一点饱和度才通透明亮，不然玻璃看起来是脏灰的
                colorFilter = if (saturate != 1f) {
                    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturate) })
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxSize()
                    // 模糊在 API 31+ 生效（RenderEffect）；更低版本自动忽略，
                    // 那时壁纸仍是清晰的 —— 比整块糊掉或直接不显示都要好
                    .then(if (effectiveBlur > 0.dp) Modifier.blur(effectiveBlur) else Modifier),
            )
        }

        // 3. 莫奈上色层：压在「底」（渐变或模糊后的壁纸）之上、遮罩与内容之下。
        //    放在遮罩之下是有意的 —— 遮罩负责文字可读性，不能被上色层顶掉。
        if (monet.size >= 3) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            // 透明度刻意压得低（16/10/14%）：这一层是铺满整屏的，
                            // 实测 34/22/30% 会把整屏平均亮度抬 15 级，卡片与文字的对比一起被冲掉；
                            // 莫奈的「存在感」主要交给上面那三团改成系统色相的光斑，
                            // 这一层只负责把整体色温拉过去
                            colors = listOf(
                                monet[0].copy(alpha = 0.16f),
                                monet[1].copy(alpha = 0.10f),
                                monet[2].copy(alpha = 0.14f),
                            ),
                            start = Offset.Zero,
                            end = Offset.Infinite,
                        ),
                    ),
            )
        }

        // 4. 壁纸遮罩
        if (wall.showsImage) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        // 近黑遮罩（与博客一致：两层 rgba(6,8,14,dim)）
                        Color(0xFF06080E).copy(
                            alpha = (wall.dim + spec.wallpaperScrim).coerceIn(0f, 0.86f),
                        ),
                    ),
            )
        }

        content()
    }
}

/**
 * 环境底的「底」：线性渐变 + 三团径向光斑（可选地换成莫奈色相）。
 *
 * **抽出来是为了让阅读页与设置页看到同一个底。** 阅读页此前自己写了一个
 * `c.backdrop.first()` 的纯色，翻页模式甚至是写死的 `Color.Black` ——
 * 于是同一个应用里两处的背景与遮罩对不上（用户反馈的「阅读页 UI 与设置不同步」）。
 * 现在两边共用这一段绘制，换风格/换深浅色/开莫奈时两处一起变。
 *
 * 这一层是**不透明**的：阅读页必须不透明（伪长图的接缝不能透出壁纸，见 1.1.5），
 * 所以这里只用实色的渐变与光斑，不引入壁纸或任何透明度依赖。
 */
@Composable
fun Modifier.ambientBase(): Modifier {
    val c = JmTheme.colors
    val spec = JmTheme.spec
    val options = LocalUiOptions.current
    val monet = if (options.monetBlur) c.monetTints else emptyList()

    return this.drawWithCache {
        val w = size.width
        val h = size.height

        // 底层线性渐变（CSS: linear-gradient(165deg, ...)）
        val base = Brush.linearGradient(
            colors = c.backdrop,
            start = Offset(0f, 0f),
            end = Offset(w * 0.35f, h),
        )

        // 三团径向光晕。强度由风格给：玻璃体系要它提供可采样的层次，
        // Material / Miuix 的底是平的（M3 的底色是 surface，HyperOS 是纯灰底），
        // 给它们铺光晕会变成「实心卡片浮在一片彩色雾上」。
        val glow = spec.backdropGlow
        val glowA = Brush.radialGradient(
            colors = listOf(
                (monet.getOrNull(0) ?: c.accent).copy(alpha = 0.45f * glow),
                Color.Transparent,
            ),
            center = Offset(w * 0.12f, -h * 0.08f),
            radius = maxOf(w, h) * 0.75f,
        )
        val glowB = Brush.radialGradient(
            colors = listOf(
                (monet.getOrNull(1) ?: c.tintWarm).copy(alpha = 0.34f * glow),
                Color.Transparent,
            ),
            center = Offset(w * 0.88f, h * 0.04f),
            radius = maxOf(w, h) * 0.68f,
        )
        // 开了「莫奈套用到模糊」时三团光斑改用系统取色的 primary / secondary / tertiary
        val glowC = Brush.radialGradient(
            colors = listOf(
                (monet.getOrNull(2) ?: c.tintCool).copy(alpha = 0.30f * glow),
                Color.Transparent,
            ),
            center = Offset(w * 0.62f, h * 1.08f),
            radius = maxOf(w, h) * 0.72f,
        )

        onDrawBehind {
            drawRect(brush = base)
            if (glow > 0f) {
                drawRect(brush = glowA)
                drawRect(brush = glowB)
                drawRect(brush = glowC)
            }
        }
    }
}
