package com.jmcomic_next.lyqs.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计令牌 —— 从博客 moyingyilang.github.io 的 `src/styles/global.css` 逐项移植。
 *
 * 视觉基调：Fluent（Windows 11 Acrylic / Mica）× MIUI（miuix）毛玻璃。
 * Fluent 提供层级、强调条与 Acrylic 颗粒质感，MIUI 提供圆角尺度、留白与橙→蓝渐变玻璃。
 *
 * 迁移约定：CSS 变量名去掉 `--` 前缀后转成大驼峰，数值原样保留，
 * 便于日后与博客对账。唯一有意为之的差异见 [Backdrop] 的注释。
 */
/**
 * 圆角阶梯。
 *
 * **这些值随风格变化**（[LocalJmSpec]），所以读法是 `@Composable` 的：
 * Windows 11 的窗口是 8dp 圆角、Material 3 的卡片是 12dp、HyperOS 是 20dp ——
 * 圆角尺度是「这套界面像谁」最直接的信号，写死就等于把五套风格抹平。
 *
 * 例外是 [pill]：胶囊形跟风格无关（哪个体系的浮动小标签都是全圆角）。
 */
object Radius {
    private val scale: RadiusScale
        @Composable @ReadOnlyComposable get() = LocalJmSpec.current.radius

    /** --r-xs */
    val xs: Dp @Composable @ReadOnlyComposable get() = scale.xs
    /** --r-sm */
    val sm: Dp @Composable @ReadOnlyComposable get() = scale.sm
    /** --r-md */
    val md: Dp @Composable @ReadOnlyComposable get() = scale.md
    /** --r-lg */
    val lg: Dp @Composable @ReadOnlyComposable get() = scale.lg
    /** --r-xl */
    val xl: Dp @Composable @ReadOnlyComposable get() = scale.xl
    /** --r-pill，胶囊形。用于页码这类短小的浮动标签。 */
    val pill: Dp = 999.dp
}

object Spacing {
    /** 4dp 基准栅格 */
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
}

object Sizing {
    /** --appbar-h，顶栏高度 */
    val appBar: Dp = 56.dp
    /** MIUI 强调条宽度（博客用 3px 的强调条标出当前项） */
    val accentBar: Dp = 3.dp
    /** 玻璃表面的发丝描边宽度 */
    val hairline: Dp = 1.dp
    /** 卡片最小触控高度，满足无障碍 48dp 下界 */
    val minTouch: Dp = 48.dp
}

/**
 * 动效参数，对应博客的 `--ease-*` 与 `--dur-*`。
 *
 * Compose 的 spring/tween 无法直接表达 cubic-bezier，这里保留时长常量，
 * 曲线以 [androidx.compose.animation.core.Easing] 在 Theme.kt 中提供。
 */
object Motion {
    /** --dur-fast */
    const val FAST = 120
    /** --dur */
    const val BASE = 200
    /** --dur-slow */
    const val SLOW = 320
    /** 入场动画的降级阈值：小于该时长不做位移，只做淡入 */
    const val FADE_ONLY = 80
}

/**
 * 玻璃表面的工艺参数。
 *
 * 博客用 `backdrop-filter: blur(40px) saturate(165%)`。Android 上没有等价的
 * 背景采样滤镜，因此改为「半透明分层面 + 环境渐变底 + 发丝描边」的组合来还原观感，
 * 具体实现见 [com.jmcomic_next.lyqs.ui.theme.GlassSurface] 与
 * [com.jmcomic_next.lyqs.ui.components.AmbientBackdrop]。
 *
 * 这里是**基准工艺**（博客那套）。实际生效的值来自 [SurfaceSpec]：
 * WindowGlass 沿用这组，Translucent 把不透明度砍到 58%、模糊加到 64dp 并铺一层强调色，
 * Miuix / Material 则完全不用玻璃（blur 0、noise 0）。
 *
 * [blurRadius] 仅在 API 31+ 由 AmbientBackdrop 真实生效（RenderEffect），
 * 低版本自动退化为纯分层面。
 */
object Glass {
    /** --blur */
    val blurRadius: Dp = 40.dp
    /** --sat，饱和度提升。Compose 无直接对应，保留数值用于文档与后续滤镜实现 */
    const val SATURATION = 1.65f
    /** --stroke-inner，玻璃内侧 1px 高光 */
    val innerHighlight = 1.dp
    /** MIUI 橙→蓝渐变薄层的角度（CSS `120deg`） */
    const val TINT_ANGLE_DEG = 120f
}

/**
 * 投影基准值（dp）。对应博客的 --shadow-sm / --shadow-card / --shadow-flyout。
 *
 * 它是**基准**而非最终值：风格在 [SurfaceSpec.shadows] 里给出自己的一套
 * （HyperOS 几乎不投影、Mica 的浮层投影更重），[Elevation] 读的是当前风格那份。
 */
object ElevationBase {
    val sm: Dp = 2.dp
    val card: Dp = 6.dp
    val flyout: Dp = 12.dp
}

/** 当前风格的投影。三档与 [com.jmcomic_next.lyqs.ui.components.GlassLevel] 一一对应。 */
object Elevation {
    val sm: Dp @Composable @ReadOnlyComposable get() = LocalJmSpec.current.surface.shadowOf(0)
    val card: Dp @Composable @ReadOnlyComposable get() = LocalJmSpec.current.surface.shadowOf(1)
    val flyout: Dp @Composable @ReadOnlyComposable get() = LocalJmSpec.current.surface.shadowOf(2)
}

/**
 * 一套完整配色。字段与博客 `:root` / `html[data-theme="dark"]` 一一对应，
 * 透明度用 [Color] 自带的 alpha 通道表达（CSS 里的 `rgba(...)`）。
 */
data class JmPalette(
    // 强调色
    val accent: Color,
    val accentHover: Color,
    val accentActive: Color,
    val accentFg: Color,
    val accentSoft: Color,
    val accentGlow: Color,

    // 表面：数字越大越靠近用户
    val surfaceMica: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val surfaceSunken: Color,
    val surfaceHover: Color,
    val surfaceActive: Color,

    // 描边
    val stroke: Color,
    val strokeStrong: Color,
    val strokeInner: Color,

    // 文字
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textOnAccent: Color,

    // 语义色（博客未定义，按 M3 规范补齐，保证错误态可用）
    val error: Color,
    val errorFg: Color,

    /** 顶栏玻璃上的 MIUI 橙→蓝渐变薄层两端色 */
    val tintWarm: Color,
    val tintCool: Color,

    /** 环境渐变底：不提供壁纸，仅用这组渐层作为 Acrylic 的采样底 */
    val backdrop: List<Color>,

    /**
     * 莫奈（动态取色）派生出的三个色相：`primary` / `secondary` / `tertiary`。
     *
     * 用来给**模糊层**上色（设置里的「莫奈取色套用在模糊上」）：
     * 只换强调色的话，背景仍是我们自己定的渐变，玻璃糊出来的颜色跟系统壁纸无关；
     * 有了这三个色相，模糊层才能跟着系统的取色走。
     * 空列表 = 没有动态取色可用，这时那个开关自然不生效。
     */
    val monetTints: List<Color> = emptyList(),
)

/** 浅色 · Mica Light + 柔和 MIUI 渐变底 */
val LightPalette = JmPalette(
    accent = Color(0xFF0F6CBD),
    accentHover = Color(0xFF115EA3),
    accentActive = Color(0xFF0C3B5E),
    accentFg = Color.White,
    accentSoft = Color(0x1A0F6CBD),
    accentGlow = Color(0x470F6CBD),

    surfaceMica = Color(0xB8F6F7FA),
    surface1 = Color(0x94FFFFFF),
    surface2 = Color(0xBCFFFFFF),
    surface3 = Color(0xDBFFFFFF),
    surfaceSunken = Color(0x090F172A),
    surfaceHover = Color(0x0D0F172A),
    surfaceActive = Color(0x140F172A),

    stroke = Color(0x170F172A),
    strokeStrong = Color(0x290F172A),
    strokeInner = Color(0xBFDFFFFF),

    text = Color(0xFF16181D),
    textSecondary = Color(0xFF4A4F5A),
    textTertiary = Color(0xFF767C88),
    textOnAccent = Color.White,

    error = Color(0xFFB3261E),
    errorFg = Color.White,

    tintWarm = Color(0x33F78736),
    tintCool = Color(0x33367DF7),

    backdrop = listOf(
        Color(0xFFEEF3FC),
        Color(0xFFF6F2FC),
        Color(0xFFEAF6FB),
    ),
)

/** 深色 · Mica Dark + 高饱和 MIUI 渐变底 */
val DarkPalette = JmPalette(
    accent = Color(0xFF60CDFF),
    accentHover = Color(0xFF7FD8FF),
    accentActive = Color(0xFF9AE2FF),
    accentFg = Color(0xFF04263A),
    accentSoft = Color(0x2460CDFF),
    accentGlow = Color(0x5760CDFF),

    surfaceMica = Color(0xB816181E),
    surface1 = Color(0x0EFFFFFF),
    surface2 = Color(0xB8262931),
    surface3 = Color(0xE630343E),
    surfaceSunken = Color(0x3D000000),
    surfaceHover = Color(0x14FFFFFF),
    surfaceActive = Color(0x21FFFFFF),

    stroke = Color(0x1AFFFFFF),
    strokeStrong = Color(0x2EFFFFFF),
    strokeInner = Color(0x1FFFFFFF),

    text = Color(0xFFF3F4F7),
    textSecondary = Color(0xB8FFFFFF),
    textTertiary = Color(0x80FFFFFF),
    textOnAccent = Color(0xFF04263A),

    error = Color(0xFFFFB4AB),
    errorFg = Color(0xFF690005),

    tintWarm = Color(0x24F78736),
    tintCool = Color(0x2E367DF7),

    backdrop = listOf(
        Color(0xFF0A0E1A),
        Color(0xFF141428),
        Color(0xFF0B1522),
    ),
)

/**
 * 字号阶梯。博客正文 15.5px / 行高 1.72，标题用 [androidx.compose.material3.Typography] 覆盖。
 */
object FontSize {
    val display = 28.sp
    val title = 20.sp
    val subtitle = 16.sp
    /** 正文：15.5px → 15.5sp */
    val body = 15.5.sp
    val label = 13.sp
    val caption = 11.5.sp
}
