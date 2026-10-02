package com.jmcomic_next.lyqs.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 五套可选风格。
 *
 * 这不是「五套配色」，而是五套**表面工艺**：圆角尺度、表面材质、描边、
 * 投影、字体层级、动效手感都不一样。只换颜色的话，五套风格在截图里会长得一样 ——
 * 那和做五个主题包没区别，用户也说不清自己为什么选了它。
 *
 * 每个风格都对着一份可查的参考：
 *
 *  - [WindowGlass]：Windows 11 窗口玻璃（8px 窗口圆角、发丝描边、Acrylic 颗粒）
 *  - [Translucent]：Windhawk 的 Translucent 系列（重度透明 + 强调色薄染）
 *  - [FlatBlur]：平面化 + 高斯模糊（没有描边/颗粒/高光，只留模糊）
 *  - [Miuix]：Xiaomi HyperOS / MIUI 的大圆角实心卡片
 *  - [Material]：Google Material You 3（完全按 M3 的颜色角色，可跟随系统动态取色）
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
    FlatBlur(
        label = "FlatBlur",
        tagline = "平面化 + 高斯模糊：无描边、无颗粒、无高光，只留一层模糊的底",
    ),
    Miuix(
        label = "Miuix",
        tagline = "HyperOS：大圆角实心卡片、无描边、弹性按压",
    ),
    Material(
        label = "Material",
        tagline = "Material You 3：按 M3 颜色角色分层，开动态取色就跟系统壁纸走",
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
 * 五种取值不是渐变关系，而是五种不同的做法，[com.jmcomic_next.lyqs.ui.components.GlassSurface]
 * 与 [com.jmcomic_next.lyqs.ui.components.AmbientBackdrop] 按它分支：
 *
 *  - [Acrylic]：半透明填充 + 背景模糊 + 发丝描边 + 上缘高光 + 颗粒（WindowGlass）
 *  - [Glass]：更低不透明度 + 强调色薄染 + 亮边（Translucent）
 *  - [Blur]：干净半透明填充 + 更重的背景模糊，**没有**描边/高光/颗粒/染色（FlatBlur）
 *  - [Card]：近实心、大圆角、**没有描边**（Miuix）
 *  - [Tonal]：实心色调分层，靠 M3 的 surfaceContainer 角色区分层级（Material）
 */
enum class SurfaceCraft { Acrylic, Glass, Blur, Card, Tonal }

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
    /** 发丝描边宽度，0dp = 不画描边（Miuix / Material / FlatBlur 都不画）。 */
    val hairline: Dp,
    /** 上缘高光：玻璃厚度的反光，实心与平面化风格不需要。 */
    val innerHighlight: Boolean,
    /**
     * 底（壁纸）的饱和度倍数（Acrylic 的 `saturate()`），1f = 不改。
     *
     * 真实 Acrylic 在模糊之后还会提饱和度：模糊会把相邻像素平均掉，颜色随之发灰，
     * 不提饱和度的话玻璃看起来是脏的灰而不是通透的彩色。
     *
     * **只在用户自己开了壁纸模糊时才应用**（见 `backdropFrosting`）：
     * 没有做磨砂的时候，壁纸就该原样不动 —— 这是 1.4.0 踩过的坑。
     */
    val backdropSaturate: Float,
    /**
     * 颗粒质感强度 0..1，Acrylic 的噪点层。
     *
     * 只在底**真的有纹理**（用户开了壁纸）时才画：颗粒的物理含义是「毛玻璃把背后的细节
     * 散射成微小亮点」，背后是平滑渐变时它没有对应物，画上去只是脏。
     * 见 [com.jmcomic_next.lyqs.ui.components.GlassSurface]。
     */
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

/**
 * 动效性格。**与 [ThemeStyle] 正交** ——「界面像谁」和「动起来是什么手感」是两件事，
 * 用户可能喜欢 HyperOS 的实心卡片，却想要 Plasma 那种柔和的过渡。
 *
 * - [Standard]：本应用原来的动效，时长偏短、曲线偏「标准」，切换干脆。
 * - [Plasma]：仿 KDE Plasma。KDE 的界面动效基本就是 Qt 的 `QEasingCurve` ——
 *   出现用 `OutCubic`、消失用 `InCubic`，时长取 Kirigami 的 long/veryLong（200/400ms）。
 *   表现出来是起步快、收尾长、位移更柔，不像原来那样「啪」地换掉。
 */
enum class MotionStyle(val label: String, val tagline: String) {
    Standard(
        label = "标准",
        tagline = "短促直接：切换干脆、位移很小",
    ),
    Plasma(
        label = "Plasma",
        tagline = "仿 KDE Plasma：出慢入快、时长更长、位移更柔",
    ),
    HyperOS(
        label = "HyperOS",
        tagline = "小米 HyperOS 的节奏：转场更长、收尾更缓，前后层次最明显",
    ),
    ;

    companion object {
        val Default = Standard
        fun fromName(name: String?): MotionStyle =
            entries.firstOrNull { it.name == name } ?: Default
    }
}

/** 动效参数。Miuix 的弹性手感不是靠时长，而是靠 spring，因此单独一个开关。 */
data class MotionSpec(
    val fast: Int,
    val base: Int,
    val slow: Int,
    val springy: Boolean,
    /** 进入（出现）曲线。 */
    val enter: Easing = JmEasing.fluent,
    /** 退出（消失）曲线。 */
    val exit: Easing = JmEasing.standard,
) {
    /**
     * 换成 Plasma 的时长与曲线，**保留该风格自己的弹性开关**。
     *
     * 时长取 Kirigami 的单位：longDuration = 200ms、veryLongDuration = 400ms。
     */
    fun asPlasma(): MotionSpec = copy(
        fast = 150,
        base = 250,
        slow = 420,
        enter = JmEasing.outCubic,
        exit = JmEasing.inCubic,
    )

    /**
     * 换成 HyperOS 的节奏。
     *
     * 三个特征，都是照着"前后关系"调的：
     *  1. **时长更长**（350/500ms）：层次变化要看得清，太短就只剩"闪一下"；
     *  2. **曲线收尾更缓**（`hyperOS` 曲线：起步快、尾巴长），像是被推到位后稳住；
     */
    fun asHyperOS(): MotionSpec = copy(
        fast = 200,
        base = 350,
        slow = 500,
        enter = JmEasing.hyperOS,
        exit = JmEasing.hyperOSOut,
    )
}

/**
 * 一套完整风格。配色不在里面 —— 配色由 [JmTheme] 按「风格 + 深浅色 + 动态取色」算出来，
 * 因为深浅色是正交的一维（五套风格 × 深浅两色 = 十个组合），塞进这里会变成十个实例。
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
    /**
     * 环境底上那三团径向光晕的强度 0..1，0 = 纯平色底。
     *
     * 光晕是博客那套 Acrylic 的做法：渐变底 + 三团光斑，给玻璃提供可采样的层次。
     * **实心体系不该有它，而且有了就不像自己**：Material You 3 的底色是平的 `surface`
     * （层级全靠 surfaceContainer 的色调阶梯），HyperOS 的列表页也是纯灰底。
     * 给它们铺一层 45% 强调色的光晕，就变成「实心卡片浮在一片彩色雾上」。
     */
    val backdropGlow: Float,
)

/** 当前生效的风格参数。拿不到直接抛异常 —— 忘包 JmTheme 的问题不该被静默吞掉。 */
val LocalJmSpec = staticCompositionLocalOf<JmSpec> {
    error("JmSpec 尚未提供：请用 JmTheme { ... } 包裹内容")
}

/**
 * 五套风格的参数表。
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
            backdropSaturate = Glass.SATURATION,
            noise = 0.5f,
            // **半透明表面不投影。** 阴影画在表面之下，而表面是透的 —— 于是阴影从材料里
            // 透出来、贴着边沿形成一圈脏灰模糊框（用户反馈「玻璃都加了一圈灰框」就是这个）。
            // 玻璃靠自身的透光与模糊表达层级，不需要投影；实心风格（Miuix / Material）
            // 仍然照常投影，那是它们表达层级的主要手段。
            shadows = listOf(0.dp, 0.dp, 0.dp),
        ),
        type = TypeScale(
            display = FontSize.display, title = FontSize.title, subtitle = FontSize.subtitle,
            body = FontSize.body, label = FontSize.label, caption = FontSize.caption,
            titleWeight = FontWeight.SemiBold, subtitleWeight = FontWeight.Medium,
            bodyWeight = FontWeight.Normal, lineHeightFactor = 1.72f,
        ),
        motion = MotionSpec(Motion.FAST, Motion.BASE, Motion.SLOW, springy = false),
        wallpaperScrim = 0.34f,
        backdropGlow = 1f,
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
            backdropSaturate = Glass.SATURATION,
            noise = 0.2f,
            // 同上：半透明表面不投影
            shadows = listOf(0.dp, 0.dp, 0.dp),
        ),
        // 壁纸透过来的更多，遮罩必须更重，否则文字压在花壁纸上没法读
        wallpaperScrim = 0.58f,
        backdropGlow = 1f,
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
            backdropSaturate = 1f,
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
        backdropGlow = 0f,
    )

    /**
     * Material You 3：颜色**完全**交给 M3 的角色体系（见 [com.jmcomic_next.lyqs.ui.theme.toJmPalette]），
     * 这里只定几何与排版。
     *
     * 与 1.3.2 之前的区别：那时抄的是 haka_comic 的 M3 用法 —— 卡片一律 elevation 0、
     * 不投影、不铺 surfaceTint。那是那个应用的风格化选择，不是 M3 规范本身。
     * 现在按规范来：形状用 M3 的 4/8/12/16/28，卡片是 medium(12dp)，
     * 层级靠 surfaceContainerLow/Container/High 的色调阶梯，浮层才带投影。
     */
    val material = JmSpec(
        style = ThemeStyle.Material,
        radius = RadiusScale(xs = 4.dp, sm = 8.dp, md = 12.dp, lg = 12.dp, xl = 28.dp),
        surface = SurfaceSpec(
            craft = SurfaceCraft.Tonal,
            fillAlphaScale = 1f,
            accentTint = 0f,
            hairline = 0.dp,
            innerHighlight = false,
            backdropSaturate = 1f,
            noise = 0f,
            // 浮起与浮层按 M3 elevation level 留投影（level1 ≈ 1dp、level3 ≈ 6dp）；
            // 卡片本身在 M3 里靠 surfaceContainer 的色调表达，不靠投影
            shadows = listOf(0.dp, 1.dp, 6.dp),
        ),
        type = TypeScale(
            display = 28.sp, title = 22.sp, subtitle = 16.sp,
            body = 16.sp, label = 14.sp, caption = 11.sp,
            titleWeight = FontWeight.Medium, subtitleWeight = FontWeight.Medium,
            bodyWeight = FontWeight.Normal, lineHeightFactor = 1.50f,
        ),
        motion = MotionSpec(100, 200, 300, springy = false),
        wallpaperScrim = 0.52f,
        backdropGlow = 0f,
    )

    /**
     * 平面化 + 高斯模糊。
     *
     * 与另外两套玻璃的区别是**只有模糊**：没有发丝描边、没有上缘高光、没有颗粒、
     * 没有强调色薄染 —— 表面就是一层干净的半透明平色，靠背后的模糊把底「推开」。
     * 玻璃那两套都有纹理（描边 / 颗粒 / 染色），这一套刻意一个都不留。
     *
     * 正因如此它的模糊要更重：没有描边和高光帮忙划边界，表面与底分不分得开全靠模糊程度。
     */
    val flatBlur = JmSpec(
        style = ThemeStyle.FlatBlur,
        // 圆角偏大：平面化风格没有描边，形状是它唯一的语言
        radius = RadiusScale(xs = 8.dp, sm = 12.dp, md = 16.dp, lg = 20.dp, xl = 28.dp),
        surface = SurfaceSpec(
            craft = SurfaceCraft.Blur,
            fillAlphaScale = 1f,
            accentTint = 0f,
            hairline = 0.dp,
            innerHighlight = false,
            backdropSaturate = 1.25f,
            noise = 0f,
            // 同上：半透明表面不投影
            shadows = listOf(0.dp, 0.dp, 0.dp),
        ),
        type = TypeScale(
            display = 28.sp, title = 21.sp, subtitle = 16.sp,
            body = 15.sp, label = 13.sp, caption = 11.5.sp,
            titleWeight = FontWeight.SemiBold, subtitleWeight = FontWeight.Medium,
            bodyWeight = FontWeight.Normal, lineHeightFactor = 1.60f,
        ),
        motion = MotionSpec(120, 220, 320, springy = false),
        wallpaperScrim = 0.42f,
        backdropGlow = 1f,
    )

    fun of(style: ThemeStyle): JmSpec = when (style) {
        ThemeStyle.WindowGlass -> windowGlass
        ThemeStyle.Translucent -> translucent
        ThemeStyle.FlatBlur -> flatBlur
        ThemeStyle.Miuix -> miuix
        ThemeStyle.Material -> material
    }
}
