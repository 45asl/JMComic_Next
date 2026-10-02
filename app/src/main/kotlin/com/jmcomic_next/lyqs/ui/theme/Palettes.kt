package com.jmcomic_next.lyqs.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 五套风格各自的配色。
 *
 * [LightPalette] / [DarkPalette]（在 Tokens.kt）是博客那套 Acrylic 玻璃配色，
 * 也就是 WindowGlass 用的那一份；另外几套在这里定义。
 *
 * 前三套（Translucent / FlatBlur / Miuix）都基于同一份底色**改写**而不是从零写：
 * 色相家族保持一致（深色都是冷灰蓝、强调色都是蓝），差别在**表面怎么叠** ——
 * 实心分层的 Miuix、半透明叠层的 Translucent、只留模糊的 FlatBlur。
 * 这样用户切换风格时看到的是「同一款应用的几种做法」，而不是几款不同应用。
 *
 * [ThemeStyle.Material] 是**例外**：它不从这里挑一份色板，而是直接用 M3 自己的颜色角色
 * （见 [toJmPalette]）—— Material You 3 的要点就是配色由规范/系统决定，而不是由应用指定。
 */

/**
 * Translucent —— Windhawk 透明系。
 *
 * 与 WindowGlass 相比只动三件事：表面不透明度砍掉近一半、描边换成更亮的一档、
 * 强调色薄染更明显。表面越透，背后的壁纸/渐变就越参与构图，这正是那个系列的做法。
 */
val TranslucentLight = LightPalette.copy(
    surfaceMica = Color(0x8CF6F7FA),
    surface1 = Color(0x5EFFFFFF),
    surface2 = Color(0x85FFFFFF),
    surface3 = Color(0xA6FFFFFF),
    surfaceSunken = Color(0x0B0F172A),
    surfaceHover = Color(0x100F172A),
    surfaceActive = Color(0x1A0F172A),
    stroke = Color(0x2E0F172A),
    strokeStrong = Color(0x420F172A),
    strokeInner = Color(0xD9FFFFFF),
    tintWarm = Color(0x40F78736),
    tintCool = Color(0x40367DF7),
)

val TranslucentDark = DarkPalette.copy(
    surfaceMica = Color(0x99101218),
    surface1 = Color(0x0AFFFFFF),
    surface2 = Color(0x7A262931),
    surface3 = Color(0xA330343E),
    surfaceSunken = Color(0x4D000000),
    surfaceHover = Color(0x1AFFFFFF),
    surfaceActive = Color(0x29FFFFFF),
    stroke = Color(0x24FFFFFF),
    strokeStrong = Color(0x3DFFFFFF),
    strokeInner = Color(0x2EFFFFFF),
    tintWarm = Color(0x2EF78736),
    tintCool = Color(0x3D367DF7),
)

/**
 * FlatBlur —— 平面化 + 高斯模糊。
 *
 * 表面是**一层干净的半透明平色**：没有描边、没有内高光、没有橙蓝薄层。
 * 这正是它与那两套玻璃的分工：WindowGlass / Translucent 靠纹理（发丝描边、颗粒、染色）
 * 表达「这是玻璃」，FlatBlur 只靠背后的模糊，所以它的填充必须更实一点
 * （没有描边帮忙划边界，填充太淡卡片就散了），描边相关令牌则全部清零。
 */
val FlatBlurLight = LightPalette.copy(
    surfaceMica = Color(0xCCF7F8FA),
    surface1 = Color(0xB3FFFFFF),
    surface2 = Color(0xD9FFFFFF),
    surface3 = Color(0xF2FFFFFF),
    stroke = Color(0x00000000),
    strokeStrong = Color(0x140F172A),
    strokeInner = Color(0x00000000),
    tintWarm = Color(0x00000000),
    tintCool = Color(0x00000000),
)

val FlatBlurDark = DarkPalette.copy(
    surfaceMica = Color(0xCC14161C),
    surface1 = Color(0x991E2128),
    surface2 = Color(0xCC262A33),
    surface3 = Color(0xE62E323C),
    surfaceSunken = Color(0x4D000000),
    surfaceHover = Color(0x14FFFFFF),
    surfaceActive = Color(0x21FFFFFF),
    stroke = Color(0x00000000),
    strokeStrong = Color(0x1FFFFFFF),
    strokeInner = Color(0x00000000),
    tintWarm = Color(0x00000000),
    tintCool = Color(0x00000000),
)

/**
 * Miuix（HyperOS / MIUI）。
 *
 * 特点是**实心**：卡片是不透明的白（深色是不透明的深灰），层级靠卡片与背景的色差，
 * 而不是透明度或描边。强调色是 HyperOS 的蓝。
 *
 * 背景刻意偏灰一点（不是纯白）：HyperOS 的列表页是「浅灰底 + 纯白卡片」，
 * 卡片才立得起来；两者都用白的话，实心卡片会糊成一片。
 */
val MiuixLight = LightPalette.copy(
    accent = Color(0xFF3482FF),
    accentHover = Color(0xFF2168E0),
    accentActive = Color(0xFF0F4FBD),
    accentFg = Color.White,
    accentSoft = Color(0x1F3482FF),
    accentGlow = Color(0x4D3482FF),

    surfaceMica = Color(0xFFF2F3F5),
    surface1 = Color(0xFFFFFFFF),
    surface2 = Color(0xFFFFFFFF),
    surface3 = Color(0xFFFFFFFF),
    surfaceSunken = Color(0x0F000000),
    surfaceHover = Color(0x0A000000),
    surfaceActive = Color(0x14000000),

    stroke = Color(0x00000000),
    strokeStrong = Color(0x14000000),
    strokeInner = Color(0x00000000),

    text = Color(0xFF0D0D0D),
    textSecondary = Color(0xFF666666),
    textTertiary = Color(0xFF999999),

    tintWarm = Color(0x00000000),
    tintCool = Color(0x00000000),
    backdrop = listOf(Color(0xFFF2F3F5), Color(0xFFF2F3F5), Color(0xFFF2F3F5)),
)

val MiuixDark = DarkPalette.copy(
    accent = Color(0xFF4C93FF),
    accentHover = Color(0xFF6BA6FF),
    accentActive = Color(0xFF8AB9FF),
    accentFg = Color(0xFF06203F),
    accentSoft = Color(0x294C93FF),
    accentGlow = Color(0x5C4C93FF),

    surfaceMica = Color(0xFF000000),
    surface1 = Color(0xFF1C1C1E),
    surface2 = Color(0xFF242426),
    surface3 = Color(0xFF2C2C2E),
    surfaceSunken = Color(0x59000000),
    surfaceHover = Color(0x14FFFFFF),
    surfaceActive = Color(0x21FFFFFF),

    stroke = Color(0x00000000),
    strokeStrong = Color(0x1FFFFFFF),
    strokeInner = Color(0x00000000),

    text = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFB3B3B3),
    textTertiary = Color(0xFF808080),

    tintWarm = Color(0x00000000),
    tintCool = Color(0x00000000),
    backdrop = listOf(Color(0xFF000000), Color(0xFF000000), Color(0xFF000000)),
)

/**
 * 把 M3 的 [ColorScheme] 映射成 [JmPalette] —— Material 风格**完全**按规范的颜色角色走。
 *
 * **这是这次把 Material 改成「真 · Material You 3」的关键一步。** 以前 Material 只是
 * 把博客那套玻璃色板换成几档蓝色，表面沿用 haka_comic 的用法（卡片一律 elevation 0、
 * 不铺 surfaceTint）。那是那个应用的风格化选择，不是 M3 规范。
 * 现在改成：M3 有什么角色就用什么角色，应用自己的令牌只是 M3 角色的一层投影 ——
 * 于是「动态取色」不再只换一个强调色，而是**整份配色**都来自系统壁纸。
 *
 * 角色对应关系（按 M3 的用法，不是拍脑袋）：
 *  - 卡片层级 → `surfaceContainerLow` → `surfaceContainer` → `surfaceContainerHigh`：
 *    M3 用**色调阶梯**表达高度，不是靠阴影
 *  - 描边 → `outlineVariant`（分隔线）/ `outline`（强描边）
 *  - 强调色容器 → `primaryContainer`（M3 的 chip / 选中态容器色）
 *
 * 一处实测过的对比度：浅色下 `primary`(#6750A4) 压 `primaryContainer`(#EADDFF) 是 5.0:1、
 * 深色下 5.4:1，都过 WCAG AA 的小字门槛 —— 所以 chip 沿用「accent 文字 + accentSoft 底」
 * 这套写法在 Material 下依然成立，不需要为它加特例。
 */
fun ColorScheme.toJmPalette(): JmPalette = JmPalette(
    accent = primary,
    // M3 的状态变化由 state layer（水波纹）表达，不靠换底色：hover/active 与 primary 同色，
    // 否则「按下时按钮换个颜色」反而是 Material 里没有的做法
    accentHover = primary,
    accentActive = primary,
    accentFg = onPrimary,
    accentSoft = primaryContainer,
    accentGlow = primary.copy(alpha = 0.32f),

    surfaceMica = surface,
    surface1 = surfaceContainerLow,
    surface2 = surfaceContainer,
    surface3 = surfaceContainerHigh,
    surfaceSunken = surfaceContainerLowest,
    surfaceHover = onSurface.copy(alpha = 0.08f),
    surfaceActive = onSurface.copy(alpha = 0.12f),

    stroke = outlineVariant,
    strokeStrong = outline,
    strokeInner = Color.Transparent,

    text = onSurface,
    textSecondary = onSurfaceVariant,
    textTertiary = onSurfaceVariant.copy(alpha = 0.74f),
    textOnAccent = onPrimary,

    error = error,
    errorFg = onError,

    // M3 没有「橙→蓝薄层」这种东西；它的染色是 primaryContainer / surfaceTint，不是叠渐变
    tintWarm = Color.Transparent,
    tintCool = Color.Transparent,

    // 层级全由 surfaceContainer 家族承担，应用底色就是 surface 本身（三层同色 = 平底）
    backdrop = listOf(surface, surface, surface),

    // Material 本身就是「整份来自 M3」，这三个色相只是给模糊层上色用
    monetTints = listOf(primary, secondary, tertiary),
)

/** 关闭动态取色时的 Material：M3 的**基线**配色（`lightColorScheme()` / `darkColorScheme()`）。 */
fun baselineM3Palette(dark: Boolean): JmPalette =
    (if (dark) darkColorScheme() else lightColorScheme()).toJmPalette()

/** 按风格取配色。Material 例外：它的配色由 M3 的颜色角色决定（见 [toJmPalette]）。 */
fun paletteFor(style: ThemeStyle, dark: Boolean): JmPalette = when (style) {
    ThemeStyle.WindowGlass -> if (dark) DarkPalette else LightPalette
    ThemeStyle.Translucent -> if (dark) TranslucentDark else TranslucentLight
    ThemeStyle.FlatBlur -> if (dark) FlatBlurDark else FlatBlurLight
    ThemeStyle.Miuix -> if (dark) MiuixDark else MiuixLight
    ThemeStyle.Material -> baselineM3Palette(dark)
}
