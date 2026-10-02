package com.jmcomic_next.lyqs.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 四套风格各自的配色。
 *
 * [LightPalette] / [DarkPalette]（在 Tokens.kt）是博客那套 Acrylic 玻璃配色，
 * 也就是 WindowGlass 用的那一份；另外三套在这里定义。
 *
 * 都基于同一份底色**改写**而不是从零写：四套风格的色相家族保持一致（深色都是冷灰蓝、
 * 强调色都是蓝），差别在**表面怎么叠**—— 实心分层的 Material / HyperOS，
 * 与半透明叠层的 Translucent。这样用户切换风格时看到的是「同一款应用的四种做法」，
 * 而不是四款不同应用。
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
 * Material 3。
 *
 * 按 M3 的色调角色取实心值：surface 是最底层，surfaceContainerLow/High/Highest
 * 逐级抬升。M3 里**层级靠色调而不是描边或透明度**，所以 stroke 与内高光都是全透明 ——
 * 留着描边就不是 Material 了。
 */
val MaterialLight = LightPalette.copy(
    accent = Color(0xFF0B57D0),
    accentHover = Color(0xFF0A4CBB),
    accentActive = Color(0xFF0842A0),
    accentFg = Color.White,
    accentSoft = Color(0xFFD3E3FD),
    accentGlow = Color(0x400B57D0),

    surfaceMica = Color(0xFFF8FAFD),
    surface1 = Color(0xFFEEF3FA),
    surface2 = Color(0xFFE6EDF7),
    surface3 = Color(0xFFDEE8F5),
    surfaceSunken = Color(0x0F0B57D0),
    surfaceHover = Color(0x140B57D0),
    surfaceActive = Color(0x1F0B57D0),

    stroke = Color(0x00000000),
    strokeStrong = Color(0xFFC4C6CF),
    strokeInner = Color(0x00000000),

    text = Color(0xFF1A1C1E),
    textSecondary = Color(0xFF44474E),
    textTertiary = Color(0xFF74777F),

    tintWarm = Color(0x00000000),
    tintCool = Color(0x00000000),
    backdrop = listOf(Color(0xFFF8FAFD), Color(0xFFF8FAFD), Color(0xFFF8FAFD)),
)

val MaterialDark = DarkPalette.copy(
    accent = Color(0xFFA8C7FA),
    accentHover = Color(0xFFC2D7FB),
    accentActive = Color(0xFFD3E3FD),
    accentFg = Color(0xFF062E6F),
    accentSoft = Color(0xFF0B57D0),
    accentGlow = Color(0x4DA8C7FA),

    surfaceMica = Color(0xFF111318),
    surface1 = Color(0xFF1B1E24),
    surface2 = Color(0xFF22262D),
    surface3 = Color(0xFF2A2E36),
    surfaceSunken = Color(0x59000000),
    surfaceHover = Color(0x14A8C7FA),
    surfaceActive = Color(0x1FA8C7FA),

    stroke = Color(0x00000000),
    strokeStrong = Color(0xFF44474E),
    strokeInner = Color(0x00000000),

    text = Color(0xFFE3E2E6),
    textSecondary = Color(0xFFC4C6CF),
    textTertiary = Color(0xFF8E9099),

    tintWarm = Color(0x00000000),
    tintCool = Color(0x00000000),
    backdrop = listOf(Color(0xFF111318), Color(0xFF111318), Color(0xFF111318)),
)

/** 按风格取配色。 */
fun paletteFor(style: ThemeStyle, dark: Boolean): JmPalette = when (style) {
    ThemeStyle.WindowGlass -> if (dark) DarkPalette else LightPalette
    ThemeStyle.Translucent -> if (dark) TranslucentDark else TranslucentLight
    ThemeStyle.Miuix -> if (dark) MiuixDark else MiuixLight
    ThemeStyle.Material -> if (dark) MaterialDark else MaterialLight
}
