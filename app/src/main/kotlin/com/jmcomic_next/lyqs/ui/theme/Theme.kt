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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat

/**
 * 当前生效的配色。用 [JmTheme.colors] 读取；拿不到时直接抛异常而不是静默回退，
 * 免得「忘了包 JmTheme」这类问题被悄悄吞掉、最后表现为颜色莫名其妙。
 */
val LocalJmPalette = staticCompositionLocalOf<JmPalette> {
    error("JmPalette 尚未提供：请用 JmTheme { ... } 包裹内容")
}

/** 动效曲线，对应博客的 --ease-standard / --ease-decel / --ease-fluent */
object JmEasing {
    /** --ease-standard: cubic-bezier(0.33, 0, 0.67, 1) */
    val standard: Easing = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)

    /** --ease-decel: cubic-bezier(0.1, 0.9, 0.2, 1) */
    val decel: Easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)

    /** --ease-fluent: cubic-bezier(0.16, 1, 0.3, 1) */
    val fluent: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
}

/**
 * 字号阶梯。字体族刻意不指定 —— 与博客一致，交给平台原生字体栈
 * （中文环境即系统默认字体），中文排版最贴近系统观感，也不额外打包字体。
 */
private val JmTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = FontSize.display,
        lineHeight = FontSize.display * 1.30f,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = FontSize.title,
        lineHeight = FontSize.title * 1.40f,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = FontSize.subtitle,
        lineHeight = FontSize.subtitle * 1.50f,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = FontSize.body,
        lineHeight = FontSize.body * 1.72f,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = FontSize.label,
        lineHeight = FontSize.label * 1.60f,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = FontSize.caption,
        lineHeight = FontSize.caption * 1.50f,
    ),
)

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
        background = backdrop.first(),
        onBackground = text,
        surface = if (dark) surfaceMica else backdrop.first(),
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
        inverseOnSurface = backdrop.first(),
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
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val palette = run {
        val blog = if (darkTheme) DarkPalette else LightPalette
        if (!useDynamic) {
            blog
        } else {
            val dyn = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            blog.withDynamicAccent(dyn, darkTheme)
        }
    }

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

    CompositionLocalProvider(LocalJmPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(darkTheme),
            typography = JmTypography,
            content = content,
        )
    }
}

/** 便捷读取当前配色，等价于 `LocalJmPalette.current`。 */
object JmTheme {
    val colors: JmPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalJmPalette.current
}
