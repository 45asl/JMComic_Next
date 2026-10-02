package com.jmcomic_next.lyqs.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.jmcomic_next.lyqs.ui.LocalUiOptions
import com.jmcomic_next.lyqs.ui.UiOptions
import androidx.core.view.WindowCompat

/**
 * 当前生效的配色。用 [JmTheme.colors] 读取；拿不到时直接抛异常而不是静默回退，
 * 免得「忘了包 JmTheme」这类问题被悄悄吞掉、最后表现为颜色莫名其妙。
 */
val LocalJmPalette = staticCompositionLocalOf<JmPalette> {
    error("JmPalette 尚未提供：请用 JmTheme { ... } 包裹内容")
}

/**
 * 动效曲线，对应博客的 --ease-standard / --ease-decel / --ease-fluent，
 * 以及 1.4.0 为「Plasma」动效性格补的 Qt 曲线。
 */
object JmEasing {
    /** --ease-standard: cubic-bezier(0.33, 0, 0.67, 1) */
    val standard: Easing = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)

    /** --ease-decel: cubic-bezier(0.1, 0.9, 0.2, 1) */
    val decel: Easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)

    /** --ease-fluent: cubic-bezier(0.16, 1, 0.3, 1) */
    val fluent: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /**
     * Qt `QEasingCurve::OutCubic`：cubic-bezier(0.215, 0.61, 0.355, 1)。
     *
     * KDE 的界面动效基本就是 Qt 那几根曲线，Plasma 里「出现/进入」用得最多的就是它 ——
     * 起步快、收尾慢，所以看起来是「被推到位」而不是「匀速滑到位」。
     */
    val outCubic: Easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

    /** Qt `QEasingCurve::InCubic`：cubic-bezier(0.55, 0.055, 0.675, 0.19)，用于「消失/退出」。 */
    val inCubic: Easing = CubicBezierEasing(0.55f, 0.055f, 0.675f, 0.19f)

    /** Qt `QEasingCurve::InOutQuad`：cubic-bezier(0.455, 0.03, 0.515, 0.955)，用于对称的往复动画。 */
    val inOutQuad: Easing = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)

    /**
     * HyperOS 的转场曲线（进入用）：**起步快、收尾很长**。
     *
     * 长尾巴是"深度"能被看清的关键 —— 后层的缩小与压暗要在最后一段慢慢停住，
     * 收得太快就只剩"闪一下"，看不出层次。
     */
    val hyperOS: Easing = CubicBezierEasing(0.35f, 0f, 0.10f, 1f)

    /** HyperOS 的转场曲线（退出用）：比进入更快收走，让前方的页迅速让位。 */
    val hyperOSOut: Easing = CubicBezierEasing(0.5f, 0f, 0.30f, 1f)
}

/**
 * 字号阶梯 **按风格生成**。
 *
 * 字体族刻意不指定 —— 与博客一致，交给平台原生字体栈（中文环境即系统默认字体），
 * 中文排版最贴近系统观感，也不额外打包字体（MiSans 之类要带字体文件，体积换不来多少）。
 *
 * 风格之间字号差别不大，**字重**差别是很明显的：HyperOS 的标题是 Bold、
 * Material 3 的标题是 Medium，同一句「我的」在两者里长得不一样，这正是识别度所在。
 */
private fun typographyOf(scale: TypeScale, shadow: Shadow? = null): Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = scale.titleWeight,
        fontSize = scale.display,
        lineHeight = scale.display * 1.30f,
        shadow = shadow,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = scale.titleWeight,
        fontSize = scale.title,
        lineHeight = scale.title * 1.40f,
        shadow = shadow,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = scale.subtitleWeight,
        fontSize = scale.subtitle,
        lineHeight = scale.subtitle * 1.50f,
        shadow = shadow,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = scale.bodyWeight,
        fontSize = scale.body,
        lineHeight = scale.body * scale.lineHeightFactor,
        shadow = shadow,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = scale.bodyWeight,
        fontSize = scale.label,
        lineHeight = scale.label * 1.60f,
        shadow = shadow,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = scale.caption,
        lineHeight = scale.caption * 1.50f,
        shadow = shadow,
    ),
)

/**
 * 通透模式下的文字处理。
 *
 * 玻璃不再覆盖底色之后，文字就直接压在壁纸上：浅色壁纸上的深色字、深色壁纸上的浅色字
 * 都会糊掉。做法是给所有文字加一圈与文字**反色**的柔光 ——
 * 深色字配白晕、浅色字配黑晕，等于给文字垫了一层底。
 *
 * 之所以不选「把壁纸压暗」：那会把这套风格唯一的目的（通透）直接抵消掉；
 * 描边/阴影的代价只落在文字上，壁纸该多清楚还是多清楚。
 */
private fun readabilityShadow(dark: Boolean): Shadow = if (dark) {
    Shadow(color = Color.Black.copy(alpha = 0.72f), offset = Offset(0f, 1f), blurRadius = 5f)
} else {
    Shadow(color = Color.White.copy(alpha = 0.90f), offset = Offset(0f, 1f), blurRadius = 5f)
}

/**
 * 把 [JmPalette] 投影到 Material 3 的 [ColorScheme]。
 *
 * 这样 Material 组件（TopAppBar、NavigationBar、Slider…）会自动跟随博客配色；
 * 而博客特有的、M3 没有对应槽位的令牌（surfaceMica、strokeInner、tint…）
 * 继续通过 [JmTheme.colors] 读取。
 */
private fun JmPalette.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val onAccentContainer = if (dark) accentHover else accentActive
    return base.copy(
        primary = accent,
        onPrimary = accentFg,
        primaryContainer = accentSoft,
        onPrimaryContainer = onAccentContainer,
        secondary = accent,
        onSecondary = accentFg,
        secondaryContainer = accentSoft,
        onSecondaryContainer = onAccentContainer,
        tertiary = accent,
        onTertiary = accentFg,
        // 实心风格（Material / Miuix）用 surfaceMica 当底：它们的 backdrop 已经是实色，
        // 而玻璃风格的 backdrop 是渐变的第一层，两者在这里含义一致
        background = surfaceMica,
        onBackground = text,
        surface = surfaceMica,
        onSurface = text,
        surfaceVariant = if (dark) surface2 else surface1,
        onSurfaceVariant = textSecondary,
        surfaceContainerLowest = if (dark) surfaceMica else surface3,
        surfaceContainerLow = surface1,
        surfaceContainer = if (dark) surface2 else surface1,
        surfaceContainerHigh = if (dark) surface3 else surface2,
        surfaceContainerHighest = surface3,
        surfaceTint = accent,
        inverseSurface = text,
        inverseOnSurface = surface1,
        outline = stroke,
        outlineVariant = strokeStrong,
        error = error,
        onError = errorFg,
        errorContainer = error.copy(alpha = 0.16f),
        onErrorContainer = error,
        scrim = Color(0x99000000),
    )
}

/**
 * 启用动态取色（Material You）时，只借系统的主色/强调色，玻璃体系仍然用博客的令牌。
 *
 * 理由：这套视觉的识别度来自「半透明分层 + 发丝描边 + 环境渐变底」，
 * 如果整个 surface 家族都被系统色替换，毛玻璃的层次感会散掉。
 */
private fun JmPalette.withDynamicAccent(dynamic: ColorScheme, dark: Boolean): JmPalette = copy(
    accent = dynamic.primary,
    accentHover = if (dark) dynamic.primaryContainer else dynamic.primary,
    accentActive = dynamic.onPrimaryContainer,
    accentFg = dynamic.onPrimary,
    accentSoft = dynamic.primary.copy(alpha = if (dark) 0.14f else 0.10f),
    accentGlow = dynamic.primary.copy(alpha = if (dark) 0.34f else 0.28f),
    // 三个色相一起带上：设置里的「莫奈取色套用在模糊上」要给模糊层上色，
    // 只有一个强调色是不够的（背景会变成单色雾）
    monetTints = listOf(dynamic.primary, dynamic.secondary, dynamic.tertiary),
)

/**
 * 应用主题入口。
 *
 * @param darkTheme 是否深色。默认跟随系统 —— 与博客首次访问的行为一致。
 * @param dynamicColor 是否启用 Material You 动态取色。默认关闭：博客的视觉识别度
 *   来自固定配色，动态取色作为可选项而非默认。
 */
@Composable
fun JmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    style: ThemeStyle = ThemeStyle.Default,
    options: UiOptions = UiOptions(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // 动效性格与界面风格正交：Plasma 只换曲线与时长，保留该风格自己的弹性开关
    // （HyperOS 的按下回弹是弹簧，不该被 Plasma 的 tween 覆盖掉）
    val styleSpec = Styles.of(style)
    val spec = when (options.motionStyle) {
        MotionStyle.Standard -> styleSpec
        MotionStyle.Plasma -> styleSpec.copy(motion = styleSpec.motion.asPlasma())
        MotionStyle.HyperOS -> styleSpec.copy(motion = styleSpec.motion.asHyperOS())
    }

    // Material You 的动态取色：系统从壁纸推出一整套颜色角色（不只是主色）。
    // 这份 scheme 有两个用途：Material 风格直接拿它当配色，另外几套玻璃风格只从里面借强调色。
    val m3Scheme = if (useDynamic) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) darkColorScheme() else lightColorScheme()
    }

    val palette = when {
        // Material 风格整份配色都来自 M3 的角色体系 —— 这才是 Material You：
        // 应用不再自带色板，而是把 M3 的角色投影成自己的令牌
        style == ThemeStyle.Material -> m3Scheme.toJmPalette()

        !useDynamic -> paletteFor(style, darkTheme)

        // 玻璃体系只借一个强调色：它们的识别度来自「半透明分层 + 发丝描边 + 环境渐变底」，
        // 如果整个 surface 家族都被系统色替换，毛玻璃的层次感会散掉
        else -> paletteFor(style, darkTheme).withDynamicAccent(m3Scheme, darkTheme)
    }

    // Material 把 M3 的 scheme 原样交给 MaterialTheme（不做有损往返转换）；
    // 其余风格由自己的调色板投影出一份 ColorScheme 给 M3 组件用
    val colorScheme = if (style == ThemeStyle.Material) m3Scheme else palette.toColorScheme(darkTheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalJmPalette provides palette,
        LocalJmSpec provides spec,
        LocalUiOptions provides options,
    ) {
        MaterialTheme(
            // 通透模式下所有文字都加一圈反色柔光，否则文字压在壁纸上会糊
            colorScheme = colorScheme,
            typography = typographyOf(
                spec.type,
                shadow = if (options.ultraTranslucent) readabilityShadow(darkTheme) else null,
            ),
            content = content,
        )
    }
}

/** 便捷读取当前配色与风格，等价于 `LocalJmPalette.current` / `LocalJmSpec.current`。 */
object JmTheme {
    val colors: JmPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalJmPalette.current

    /** 当前风格的表面/圆角/字体/动效参数。 */
    val spec: JmSpec
        @Composable
        @ReadOnlyComposable
        get() = LocalJmSpec.current

    /** 当前风格的动效时长（毫秒）。 */
    val motion: MotionSpec
        @Composable
        @ReadOnlyComposable
        get() = LocalJmSpec.current.motion
}
