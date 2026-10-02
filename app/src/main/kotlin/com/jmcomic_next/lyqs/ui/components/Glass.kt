package com.jmcomic_next.lyqs.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jmcomic_next.lyqs.ui.LocalWallpaper
import com.jmcomic_next.lyqs.ui.theme.jmShape
import com.jmcomic_next.lyqs.ui.theme.Elevation
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Radius
import com.jmcomic_next.lyqs.ui.theme.Sizing
import com.jmcomic_next.lyqs.ui.theme.SurfaceCraft

/**
 * 玻璃表面的层级。
 *
 * 对应博客的三档表面令牌 —— 数字越大越靠近用户、越不透明、投影越重：
 * [Card] → `--surface-1`，[Raised] → `--surface-2`，[Flyout] → `--surface-3`。
 */
enum class GlassLevel { Card, Raised, Flyout }

/**
 * 一层表面（卡片 / 浮起 / 浮层）。
 *
 * **它是五套风格的公共落点**：13 个界面文件、几十处调用都走这里，所以「风格怎么画表面」
 * 只需要在这一个函数里分支，页面完全不用知道自己正跑在哪套风格下。
 *
 * 四种工艺（[SurfaceCraft]）：
 *
 *  1. **Acrylic（WindowGlass）** —— 博客那套：半透明填充 + 模糊 + 发丝描边 + 上缘高光 + 颗粒。
 *     对应 Windows 11 的 Acrylic 窗口材质。
 *  2. **Glass（Translucent）** —— 同样的层，但不透明度砍到 58%、模糊加到 64dp，
 *     再铺一层强调色薄染、描边换成更亮的一档。透出来的背景明显更多。
 *  3. **Card（Miuix）** —— 实心卡片 + 大圆角，**不画描边也不模糊**：HyperOS 靠色差分层。
 *  4. **Tonal（Material）** —— 实心色调分层，靠 surface 家族的色调阶梯区分层级，同样无描边。
 *
 * 关于模糊：Compose 读不到「已绘制内容」再作模糊，所以真正的背景模糊由
 * [AmbientBackdrop] 的 RenderEffect 完成（API 31+），这里的 blur 值只是**协议**：
 * 它决定表面允许多少背景透上来，以及是否加颗粒层。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.Card,
    shape: Shape = jmShape(Radius.lg),
    tinted: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = JmTheme.colors
    val surface = JmTheme.spec.surface
    val spec = JmTheme.spec
    val noiseBrush = rememberNoiseBrush()

    // 底有没有真实纹理（用户开了壁纸）。Acrylic 的颗粒只在有东西可散射时才画 ——
    // 判断放在这里而不是令牌里：同一个风格在「有壁纸 / 没壁纸」下应该表现不同，
    // 这是材质的性质，不是风格的参数。
    val backdropHasTexture = LocalWallpaper.current.showsImage

    // 按压反馈：HyperOS 的卡片按下会微微缩一下（弹性），其它风格保持 1f 不缩放
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) surface.pressScale else 1f,
        animationSpec = if (spec.motion.springy) {
            spring(dampingRatio = 0.55f, stiffness = 900f)
        } else {
            tween(spec.motion.fast)
        },
        label = "surfacePress",
    )

    val raw = when (level) {
        GlassLevel.Card -> c.surface1
        GlassLevel.Raised -> c.surface2
        GlassLevel.Flyout -> c.surface3
    }
    // 风格只做整体增减：调色板里深浅色各自的不透明度是设计的一部分
    val fill = raw.copy(alpha = (raw.alpha * surface.fillAlphaScale).coerceIn(0f, 1f))
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
            // 缩放必须放在最外层。放在 `.background(...)` / `.border(...)` **之内**的话，
            // graphicsLayer 只包住更内层的内容，结果是「卡片不动、卡片里的字在缩」——
            // Miuix 的 0.97 弹性按压会变成文字自己在抖。
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(fill)
            .then(
                // 强调色薄染（Translucent）：铺在填充之上、内容之下，像 Windhawk 把
                // 系统强调色染到窗口上一样
                if (surface.accentTint > 0f) {
                    Modifier.drawBehind {
                        drawRect(color = c.accent.copy(alpha = surface.accentTint))
                    }
                } else {
                    Modifier
                },
            )
            .then(
                // 薄层只属于玻璃风格：Material / Miuix / FlatBlur 的表面是平的
                // （haka_comic 的 AppBarTheme 就是 scrolledUnderElevation 0 + 透明 surfaceTint），
                // 给它们加一层橙蓝渐变就不是那几套风格了
                if (tinted && (surface.craft == SurfaceCraft.Acrylic ||
                        surface.craft == SurfaceCraft.Glass)) {
                    // drawBehind 而不是 drawWithContent：薄层是**材质的一部分**，
                    // 要铺在文字下面。原来的写法把薄层盖在内容上，等于给整块玻璃上的字
                    // 蒙了一层淡色 —— Translucent 那种 12% 的强调色染会直接糊掉文字
                    Modifier.drawBehind {
                        drawRect(brush = tintBrush)
                    }
                } else {
                    Modifier
                },
            )
            .then(
                if (surface.hairline > 0.dp) {
                    Modifier.border(surface.hairline, c.stroke, shape)
                } else {
                    Modifier
                },
            )
            .drawBehind {
                // 颗粒质感：Acrylic 的噪点层。用一张预生成的 64×64 噪点贴图平铺，
                // 而不是每帧画上千个小圆 —— 后者在滚动列表里会直接掉帧。
                //
                // **只在底真的有纹理时才画。** 颗粒的物理含义是「毛玻璃把背后的细节散射成
                // 微小亮点」；底是平滑渐变时它没有可散射的对象，画上去只是脏 ——
                // 而且 64px 的贴图在 2.75 倍密度下是约 23dp 的周期，眼睛能看出重复。
                // 所以它跟「用户有没有开壁纸」绑定，而不是无条件铺在每张卡上。
                if (surface.noise > 0f && backdropHasTexture) {
                    // **必须用 Overlay（中点灰 = 不变），不能用默认的 SrcOver。**
                    // 贴图的均值是 127/255，SrcOver 合成等于把整块表面往中灰拉：
                    // 实测底 0.10 时整层被提亮 12.8 级、底 0.97 时被压暗 15.1 级，
                    // 而它本该提供的颗粒只有 ±3 级 —— 也就是说这一层 5 倍于颗粒的成分
                    // 是一层灰膜，玻璃因此发灰、发脏、掉饱和度。
                    // Overlay 把均值偏移压到 0.00 级（实测），只留下颗粒本身。
                    drawRect(
                        brush = noiseBrush,
                        alpha = surface.noise,
                        blendMode = BlendMode.Overlay,
                    )
                }
                // 上缘高光：一条很短的竖向渐变，只留 3dp 高度，模拟玻璃的厚度反光
                if (surface.innerHighlight) {
                    val h = surface.hairline.toPx() * 3f
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(c.strokeInner, Color.Transparent),
                            startY = 0f,
                            endY = h,
                        ),
                        size = Size(size.width, h),
                    )
                }
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
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

/**
 * 一张 64×64 的噪点贴图，平铺使用 —— Acrylic 颗粒质感的实现方式。
 *
 * 生成一次、remember 住：噪点必须是**固定的**（每帧随机就是闪动的雪花点，比没有还难看），
 * 而且要能平铺（贴图尺寸取 2 的幂，接缝不明显）。用确定性伪随机而不是 Random，
 * 这样深浅两套主题、五套风格拿到的颗粒一模一样。
 *
 * **它的均值是 127/255，所以只能配中性混合模式（Overlay）使用。**
 * 用默认的 SrcOver 会变成往中灰拉的灰膜：实测整块表面被偏移 +12.8 级（深色底）
 * 到 −15.1 级（近白底），而它本该提供的颗粒只有 ±3 级 —— 灰膜是颗粒的 5 倍。
 * 那正是「玻璃发灰、发脏、掉饱和度」的来源（深色卡饱和度从 0.392 掉到 0.266）。
 */
@Composable
private fun rememberNoiseBrush(): ShaderBrush = remember {
    val size = 64
    val pixels = IntArray(size * size)
    var seed = 0x9E3779B9u
    for (i in pixels.indices) {
        // xorshift：确定性、无依赖、够随机
        seed = seed xor (seed shl 13)
        seed = seed xor (seed shr 17)
        seed = seed xor (seed shl 5)
        val v = (seed and 0xFFu).toInt()
        val lum = 128 + (v - 128) / 3
        // alpha 0x80：Overlay 下颗粒幅度正比于这一层的不透明度，原来配 SrcOver 用的
        // 0x40 太淡（同 alpha 下实测只有 ±1.9 级）。改成中性混合后可以放心加大，
        // 因为均值不再偏移 —— 幅度不再等于「灰膜」。
        pixels[i] = (0x80 shl 24) or (lum shl 16) or (lum shl 8) or lum
    }
    val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}
