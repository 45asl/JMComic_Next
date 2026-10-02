package com.jmcomic_next.lyqs.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 四套可选风格。
 *
 * 这不是「四套配色」，而是四套**表面工艺**：圆角尺度、表面材质、描边、
 * 投影、字体层级、动效手感都不一样。只换颜色的话，四套风格在截图里会长得一样 ——
 * 那和做四个主题包没区别，用户也说不清自己为什么选了它。
 *
 * 每个风格都对着一份可查的参考：
 *
 *  - [WindowGlass]：Windows 11 窗口玻璃（8px 窗口圆角、发丝描边、Acrylic 颗粒）
 *  - [Translucent]：Windhawk 的 Translucent 系列（重度透明 + 强调色薄染）
 *  - [Miuix]：Xiaomi HyperOS / MIUI 的大圆角实心卡片
 *  - [Material]：经典 Material 3（色调分层，无玻璃无描边）
 *
 * 默认是 [WindowGlass]：那是本应用原来的样子（博客的 Fluent × MIUI 玻璃），
 * 升级不该把老用户的界面换掉。
 */
enum class ThemeStyle(val label: String, val tagline: String) {
    WindowGlass(
        label = "WindowGlass",
        tagline = "Windows 11 窗口玻璃：8dp 圆角 + 发丝描边 + Acrylic 颗粒",
    ),
    Translucent(
        label = "Translucent",
        tagline = "Windhawk 透明系：表面更透、强调色薄染，壁纸透得最明显",
    ),
    Miuix(
        label = "Miuix",
        tagline = "HyperOS：大圆角实心卡片、无描边、弹性按压",
    ),
    Material(
        label = "Material",
        tagline = "经典 Material 3：色调分层表面，没有玻璃也没有描边",
    ),
    ;

    companion object {
        val Default = WindowGlass

        /** 从持久化的名字还原，认不出来就用默认 —— 不因为一个脏值让应用起不来。 */
        fun fromName(name: String?): ThemeStyle =
            entries.firstOrNull { it.name == name } ?: Default
    }
}

/** 圆角尺度。同一套风格内所有圆角都从这里取，避免出现「随手写 14dp」的散值。 */
data class RadiusScale(
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
)

/**
 * 表面工艺 —— 决定一块「卡片」到底怎么画。
 *
 * 四种取值不是渐变关系，而是四种不同的做法，[com.jmcomic_next.lyqs.ui.components.GlassSurface]
 * 按它分支：
 *
 *  - [Acrylic]：半透明填充 + 模糊 + 发丝描边 + 上缘高光（WindowGlass）
 *  - [Glass]：更低不透明度 + 强调色薄染 + 亮边（Translucent）
 *  - [Card]：近实心、大圆角、**没有描边**（Miuix）
 *  - [Tonal]：实心色调分层，靠高度而不是描边区分层级（Material）
 */
enum class SurfaceCraft { Acrylic, Glass, Card, Tonal }

/**
 * 表面参数。
 *
 * [fillAlphaScale] 是**乘在调色板 alpha 上的倍数**而不是绝对值：调色板里
 * 深浅色各自已经有一套不透明度（那是设计的一部分），风格只在此基础上做整体增减。
 */
data class SurfaceSpec(
    val craft: SurfaceCraft,
    val fillAlphaScale: Float,
    /** 强调色薄染强度 0..1。Windhawk 的 Translucent 系会把强调色铺在窗口上，这里同理。 */
    val accentTint: Float,
    /** 发丝描边宽度，0dp = 不画描边（Miuix / Material 都不画）。 */
    val hairline: Dp,
    /** 上缘高光：玻璃厚度的反光，实心卡片不需要。 */
    val innerHighlight: Boolean,
    /** 背景模糊半径，0dp = 不用 RenderEffect（API 31+ 才真实生效）。 */
    val blur: Dp,
    /** 颗粒质感强度 0..1，Acrylic 的噪点层。 */
    val noise: Float,
    /** [GlassLevel] 三档对应的投影：Card / Raised / Flyout。 */
    val shadows: List<Dp>,
    /** 按下时的缩放（Miuix 的弹性手感），1f = 不缩放。 */
    val pressScale: Float = 1f,
) {
    fun shadowOf(level: Int): Dp = shadows.getOrElse(level) { shadows.lastOrNull() ?: 0.dp }
}

/** 字号与字重。风格之间字号差异不大，但**字重**差异决定了「像谁」。 */
data class TypeScale(
    val display: TextUnit,
    val title: TextUnit,
    val subtitle: TextUnit,
    val body: TextUnit,
    val label: TextUnit,
    val caption: TextUnit,
    val titleWeight: FontWeight,
    val subtitleWeight: FontWeight,
    val bodyWeight: FontWeight,
    val lineHeightFactor: Float,
)

/** 动效参数。Miuix 的弹性手感不是靠时长，而是靠 spring，因此单独一个开关。 */
data class MotionSpec(
    val fast: Int,
    val base: Int,
    val slow: Int,
    val springy: Boolean,
)

/**
 * 一套完整风格。配色不在里面 —— 配色由 [JmTheme] 按「风格 + 深浅色 + 动态取色」算出来，
 * 因为深浅色是正交的一维（四套风格 × 深浅两色 = 八个组合），塞进这里会变成八个实例。
 */
data class JmSpec(
    val style: ThemeStyle,
    val radius: RadiusScale,
    val surface: SurfaceSpec,
    val type: TypeScale,
    val motion: MotionSpec,
    /**
     * 壁纸上的遮罩强度 0..1。
     *
     * 有壁纸时文字必须还能读：玻璃越透，底就越需要压暗/压亮一层。
     * Material/Miuix 的表面本身是实心的，只需要很轻的遮罩。
     */
    val wallpaperScrim: Float,
)

/** 当前生效的风格参数。拿不到直接抛异常 —— 忘包 JmTheme 的问题不该被静默吞掉。 */
val LocalJmSpec = staticCompositionLocalOf<JmSpec> {
    error("JmSpec 尚未提供：请用 JmTheme { ... } 包裹内容")
}

/**
 * 四套风格的参数表。
 *
 * 数值来源分两类，注释里逐条注明：
 *  - **有出处**：Windows 11 的窗口圆角是 8px、控件 4px（Fluent 设计规范）；Material 3 的
 *    圆角阶梯是 4/8/12/16/28；这两条是公开规范，直接照搬。
 *  - **按观感定的**：不透明度、模糊半径、颗粒强度这些没有公开规范可依（Windhawk 的 mod
 *    也是给滑杆让人自己拖），取值以「在真机上肉眼可辨」为准，并写进 CHANGELOG 供对账。
 */
object Styles {

    /** Windows 11：窗口 8px 圆角、控件 4px；Acrylic 的噪点与 40dp 模糊沿用博客的工艺。 */
    val windowGlass = JmSpec(
        style = ThemeStyle.WindowGlass,
        radius = RadiusScale(xs = 2.dp, sm = 4.dp, md = 6.dp, lg = 8.dp, xl = 12.dp),
        surface = SurfaceSpec(
            craft = SurfaceCraft.Acrylic,
            fillAlphaScale = 1f,
            accentTint = 0f,
            hairline = 1.dp,
            innerHighlight = true,
            blur = Glass.blurRadius,
            noise = 0.5f,
            shadows = listOf(ElevationBase.sm, ElevationBase.card, ElevationBase.flyout),
        ),
        type = TypeScale(
            display = FontSize.display, title = FontSize.title, subtitle = FontSize.subtitle,
            body = FontSize.body, label = FontSize.label, caption = FontSize.caption,
            titleWeight = FontWeight.SemiBold, subtitleWeight = FontWeight.Medium,
            bodyWeight = FontWeight.Normal, lineHeightFactor = 1.72f,
        ),
        motion = MotionSpec(Motion.FAST, Motion.BASE, Motion.SLOW, springy = false),
        wallpaperScrim = 0.34f,
    )

    /**
     * Windhawk 的 Translucent 系：与 WindowGlass 同一套几何（都是 Windows 的窗口），
     * 差别全在**透明度与染色**上 —— 表面不透明度砍掉近一半、铺一层强调色、
     * 描边换成更亮的版本，壁纸透出来的程度因此明显不同。
     */
    val translucent = windowGlass.copy(
        style = ThemeStyle.Translucent,
        surface = windowGlass.surface.copy(
            craft = SurfaceCraft.Glass,
            fillAlphaScale = 0.58f,
            accentTint = 0.12f,
            blur = 64.dp,
            noise = 0.2f,
            shadows = listOf(1.dp, 3.dp, 8.dp),
        ),
        // 壁纸透过来的更多，遮罩必须更重，否则文字压在花壁纸上没法读
        wallpaperScrim = 0.58f,
    )

    /** HyperOS：大圆角、实心卡片、不画描边，靠色差分层；弹性按压是它的标志手感。 */
    val miuix = JmSpec(
        style = ThemeStyle.Miuix,
        // 圆角取研究给出的区间：小米自家规范里小组件圆角是 1080p 下 38px=12.67dp、
        // 2k 下 50px=14.48dp，Miuix 组件库的卡片/按钮是 16dp，因此卡片落 16dp。
        // 另外这个风格用的是**连续圆角**（见 SquircleShape），不是四分之一圆弧
        radius = RadiusScale(xs = 4.dp, sm = 8.dp, md = 12.dp, lg = 16.dp, xl = 24.dp),
        surface = SurfaceSpec(
            craft = SurfaceCraft.Card,
            fillAlphaScale = 1f,
            accentTint = 0f,
            hairline = 0.dp,
            innerHighlight = false,
            blur = 0.dp,
            noise = 0f,
            // HyperOS 的卡片几乎不投影，层级靠「卡片比背景亮/暗一档」
            shadows = listOf(0.dp, 1.dp, 3.dp),
            pressScale = 0.97f,
        ),
        type = TypeScale(
            display = 30.sp, title = 22.sp, subtitle = 16.sp,
            body = 15.sp, label = 13.5.sp, caption = 12.sp,
            titleWeight = FontWeight.Bold, subtitleWeight = FontWeight.SemiBold,
            bodyWeight = FontWeight.Medium, lineHeightFactor = 1.45f,
        ),
        motion = MotionSpec(150, 240, 360, springy = true),
        wallpaperScrim = 0.46f,
    )

    /** Material 3：圆角用规范的 4/8/12/16/28；表面实心、靠 tonal elevation 分层。 */
    val material = JmSpec(
        style = ThemeStyle.Material,
        // 卡片 12dp：M3 自己的默认卡片圆角，也是 haka_comic 里最常用的那一档（它用 8~12）
        radius = RadiusScale(xs = 4.dp, sm = 8.dp, md = 12.dp, lg = 12.dp, xl = 28.dp),
        surface = SurfaceSpec(
            craft = SurfaceCraft.Tonal,
            fillAlphaScale = 1f,
            accentTint = 0f,
            hairline = 0.dp,
            innerHighlight = false,
            blur = 0.dp,
            noise = 0f,
            // 卡片与浮起都**不投影**（haka_comic 里 7 处 Card 全部 elevation: 0），
            // 高度靠 surfaceContainer 家族的色调差表达；只有浮层留一点投影
            shadows = listOf(0.dp, 0.dp, 3.dp),
        ),
        type = TypeScale(
            display = 28.sp, title = 22.sp, subtitle = 16.sp,
            body = 16.sp, label = 14.sp, caption = 11.sp,
            titleWeight = FontWeight.Medium, subtitleWeight = FontWeight.Medium,
            bodyWeight = FontWeight.Normal, lineHeightFactor = 1.50f,
        ),
        motion = MotionSpec(100, 200, 300, springy = false),
        wallpaperScrim = 0.52f,
    )

    fun of(style: ThemeStyle): JmSpec = when (style) {
        ThemeStyle.WindowGlass -> windowGlass
        ThemeStyle.Translucent -> translucent
        ThemeStyle.Miuix -> miuix
        ThemeStyle.Material -> material
    }
}
