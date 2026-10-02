package com.jmcomic_next.lyqs

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jmcomic_next.lyqs.ui.UiOptions
import com.jmcomic_next.lyqs.ui.jmComicSharedKey
import com.jmcomic_next.lyqs.ui.components.backdropFrosting
import com.jmcomic_next.lyqs.ui.theme.JmEasing
import com.jmcomic_next.lyqs.ui.theme.MotionStyle
import com.jmcomic_next.lyqs.ui.theme.RadiusScale
import com.jmcomic_next.lyqs.ui.theme.Styles
import com.jmcomic_next.lyqs.ui.theme.SurfaceCraft
import com.jmcomic_next.lyqs.ui.theme.ThemeStyle
import com.jmcomic_next.lyqs.ui.theme.baselineM3Palette
import com.jmcomic_next.lyqs.ui.theme.paletteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 五套风格的令牌。
 *
 * 这些断言看起来「只是查表」，但它们钉住的是一件事：**五套风格必须真的不一样**。
 * 风格系统最容易出的问题不是崩溃，而是「做了五个选项，看起来却差不多」——
 * 圆角抄成同一组、表面工艺都写成 Acrylic、字重没换，用户选了半天没感觉。
 * 所以这里逐项要求彼此不同，而不是只测「有五个枚举值」。
 *
 * 1.3.3 又补了两类断言：**模糊/饱和度必须真的接线**（以前 `blur` 令牌是死值，
 * 声明了 40dp 却没有任何地方读它），以及 **Material 必须真的按 M3 的角色走**
 * （以前只是把玻璃色板换成几档蓝色）。
 */
class ThemeStyleTest {

    @Test
    fun `default style keeps the original look`() {
        // 升级不该把老用户的界面换掉：默认必须还是博客那套（WindowGlass + Acrylic）
        assertEquals(ThemeStyle.WindowGlass, ThemeStyle.Default)
        assertEquals(SurfaceCraft.Acrylic, Styles.of(ThemeStyle.Default).surface.craft)
    }

    @Test
    fun `fromName tolerates junk`() {
        assertEquals(ThemeStyle.Miuix, ThemeStyle.fromName("Miuix"))
        assertEquals(ThemeStyle.Default, ThemeStyle.fromName(null))
        assertEquals(ThemeStyle.Default, ThemeStyle.fromName("Materail"))
        assertEquals(ThemeStyle.FlatBlur, ThemeStyle.fromName("FlatBlur"))
    }

    @Test
    fun `each style draws surfaces its own way`() {
        val crafts = ThemeStyle.entries.map { Styles.of(it).surface.craft }
        assertEquals(crafts.size, crafts.toSet().size)
        assertEquals(SurfaceCraft.Card, Styles.of(ThemeStyle.Miuix).surface.craft)
        assertEquals(SurfaceCraft.Tonal, Styles.of(ThemeStyle.Material).surface.craft)
        assertEquals(SurfaceCraft.Glass, Styles.of(ThemeStyle.Translucent).surface.craft)
        assertEquals(SurfaceCraft.Acrylic, Styles.of(ThemeStyle.WindowGlass).surface.craft)
        assertEquals(SurfaceCraft.Blur, Styles.of(ThemeStyle.FlatBlur).surface.craft)
    }

    @Test
    fun `translucent is actually more transparent than window glass`() {
        // 这两个风格同出 Windhawk 的窗口玻璃一族，差别就该体现在「透多少」上
        val acrylic = Styles.of(ThemeStyle.WindowGlass).surface
        val glass = Styles.of(ThemeStyle.Translucent).surface
        assertTrue("Translucent 的填充必须更透", glass.fillAlphaScale < acrylic.fillAlphaScale)
        assertTrue("Translucent 要铺强调色染", glass.accentTint > acrylic.accentTint)
        // 「谁的模糊更强」这条已经没有意义：风格不再自带模糊（见 backdropFrosting）
    }

    @Test
    fun `styles never blur the wallpaper behind the user's back`() {
        // 这条钉的是一次真 bug（1.4.0）：风格的模糊令牌被 `maxOf(风格, 用户设置)` 应用，
        // 于是用户把壁纸模糊调到 0（要清晰）也会被强制糊掉 —— 壁纸变成常驻模糊。
        //
        // 规则：**模糊与饱和度只由用户的壁纸设置决定**。
        // 用户设 0 → 既不能模糊，也不能动颜色（没做磨砂就别改壁纸）。
        val off = backdropFrosting(userBlur = 0, styleSaturate = 1.65f)
        assertEquals("用户设 0 时不得模糊", 0f, off.blur.value, 0.001f)
        assertEquals("用户设 0 时不得改壁纸饱和度", 1f, off.saturate, 0.001f)

        // 用户开了模糊 → 用他自己的值，并做 Acrylic 的饱和度补偿
        val on = backdropFrosting(userBlur = 12, styleSaturate = 1.65f)
        assertEquals("必须用用户的值", 12f, on.blur.value, 0.001f)
        assertEquals(1.65f, on.saturate, 0.001f)

        // 风格侧只允许提供「补偿倍数」，不允许提供模糊半径
        listOf(ThemeStyle.WindowGlass, ThemeStyle.Translucent, ThemeStyle.FlatBlur).forEach { style ->
            assertTrue(
                "$style 的饱和度补偿应当 > 1（模糊后发灰需要补）",
                Styles.of(style).surface.backdropSaturate > 1f,
            )
        }
        listOf(ThemeStyle.Miuix, ThemeStyle.Material).forEach { style ->
            assertEquals(
                "$style 是实心体系，不该改壁纸饱和度",
                1f,
                Styles.of(style).surface.backdropSaturate,
                0.001f,
            )
        }
    }

    @Test
    fun `flat blur is the one with no texture at all`() {
        // FlatBlur 的定义就是「只有模糊」：描边、内高光、颗粒、强调色染一个都不留。
        // 少任何一条它就跟 WindowGlass 分不开了
        val flat = Styles.of(ThemeStyle.FlatBlur).surface
        assertEquals(0f, flat.hairline.value, 0.001f)
        assertFalse(flat.innerHighlight)
        assertEquals(0f, flat.noise, 0.001f)
        assertEquals(0f, flat.accentTint, 0.001f)
        // 说明：它原来还带一个 56dp 的「底模糊」，那是在风格里强制糊壁纸，
        // 已经被移除（见 `styles never blur the wallpaper behind the user's back`）。
    }

    @Test
    fun `solid styles do not use glass or borders`() {
        // Miuix / Material 是实心体系：既没有描边，也没有模糊与颗粒。
        // 留下任何一项都会让它看起来像「换了颜色的玻璃」，那就白做了
        listOf(ThemeStyle.Miuix, ThemeStyle.Material).forEach { style ->
            val s = Styles.of(style).surface
            assertEquals("$style 不该画描边", 0f, s.hairline.value, 0.001f)
            assertEquals("$style 不该改壁纸饱和度", 1f, s.backdropSaturate, 0.001f)
            assertEquals("$style 不该有颗粒层", 0f, s.noise, 0.001f)
            assertFalse("$style 不该有上缘高光", s.innerHighlight)
        }
    }

    @Test
    fun `radius scales differ and stay ordered`() {
        fun RadiusScale.ordered() = listOf(xs, sm, md, lg, xl).map { it.value }
        ThemeStyle.entries.forEach { style ->
            val v = Styles.of(style).radius.ordered()
            assertEquals("$style 的圆角必须递增", v.sorted(), v)
        }
        // Windows 11 的窗口圆角是 8dp、HyperOS 的卡片是 20dp，两者不该撞在一起
        assertNotEquals(
            Styles.of(ThemeStyle.WindowGlass).radius.lg,
            Styles.of(ThemeStyle.Miuix).radius.lg,
        )
        assertEquals(8f, Styles.of(ThemeStyle.WindowGlass).radius.lg.value, 0.001f)
        // 小米自家规范：小组件圆角 1080p 下 38px = 12.67dp、2k 下 50px = 14.48dp；
        // Miuix 组件库的卡片取 16dp，落在这个区间里
        assertEquals(16f, Styles.of(ThemeStyle.Miuix).radius.lg.value, 0.001f)
        assertTrue(Styles.of(ThemeStyle.Miuix).radius.lg.value in 12.5f..16f)
    }

    @Test
    fun `press feedback only where the style calls for it`() {
        assertEquals(1f, Styles.of(ThemeStyle.Material).surface.pressScale, 0.001f)
        assertTrue(Styles.of(ThemeStyle.Miuix).surface.pressScale < 1f)
        assertTrue(Styles.of(ThemeStyle.Miuix).motion.springy)
        assertFalse(Styles.of(ThemeStyle.WindowGlass).motion.springy)
    }

    @Test
    fun `every style has a palette for both themes`() {
        ThemeStyle.entries.forEach { style ->
            listOf(true, false).forEach { dark ->
                val p = paletteFor(style, dark)
                // 实心风格的表面必须是不透明的，否则「实心」只是名义上的
                if (style == ThemeStyle.Miuix || style == ThemeStyle.Material) {
                    assertEquals("$style 的表面必须不透明", 1f, p.surface3.alpha, 0.001f)
                    assertEquals("$style 的底色必须不透明", 1f, p.backdrop.first().alpha, 0.001f)
                }
                // 玻璃风格得留出透光的余地
                if (style == ThemeStyle.WindowGlass || style == ThemeStyle.Translucent) {
                    assertTrue("$style 的表面应当是半透明的", p.surface1.alpha < 0.9f)
                }
            }
        }
    }

    @Test
    fun `material follows the M3 shape and elevation levels`() {
        // 从「照抄 haka_comic」改成「按 M3 规范」之后：
        //  - 卡片是 M3 的 medium 形状（12dp）
        //  - filled card 不投影，但浮层按 M3 的 elevation level 要投（level3 ≈ 6dp）
        val spec = Styles.of(ThemeStyle.Material)
        assertEquals(12f, spec.radius.lg.value, 0.001f)
        assertEquals(28f, spec.radius.xl.value, 0.001f)
        assertEquals("filled card 不投影", 0f, spec.surface.shadowOf(0).value, 0.001f)
        assertTrue("浮层要按 M3 的高度投影", spec.surface.shadowOf(2).value > 0f)
    }

    @Test
    fun `material takes its colors from the M3 roles`() {
        // 这条是「真 · Material You 3」的根据：Material 的令牌必须是 M3 颜色角色本身，
        // 不是应用自己挑的一批颜色。以前它只是把玻璃色板换成几档蓝色，那就不是 M3 了。
        listOf(true, false).forEach { dark ->
            val scheme = if (dark) darkColorScheme() else lightColorScheme()
            val p = baselineM3Palette(dark)
            assertEquals("强调色必须是 primary", scheme.primary, p.accent)
            assertEquals("强调色上的文字必须是 onPrimary", scheme.onPrimary, p.accentFg)
            assertEquals("卡片容器色必须是 surfaceContainerLow", scheme.surfaceContainerLow, p.surface1)
            assertEquals("中层必须是 surfaceContainer", scheme.surfaceContainer, p.surface2)
            assertEquals("高层必须是 surfaceContainerHigh", scheme.surfaceContainerHigh, p.surface3)
            assertEquals("正文色必须是 onSurface", scheme.onSurface, p.text)
            assertEquals("次级文字必须是 onSurfaceVariant", scheme.onSurfaceVariant, p.textSecondary)
            assertEquals("描边必须是 outlineVariant", scheme.outlineVariant, p.stroke)
            // M3 的层级靠色调阶梯而不是透明度：三层必须是三个不同的实色
            assertEquals("M3 的表面必须不透明", 1f, p.surface3.alpha, 0.001f)
            assertNotEquals("三层容器色必须彼此不同", p.surface1, p.surface2)
            assertNotEquals("三层容器色必须彼此不同", p.surface2, p.surface3)
            // M3 没有橙蓝薄层
            assertEquals(Color.Transparent, p.tintWarm)
            assertEquals(Color.Transparent, p.tintCool)
        }
    }

    @Test
    fun `only miuix uses continuous corners`() {
        // HyperOS 的圆角是「平滑圆角」（连续曲率），不是四分之一圆弧；
        // 另外三套保持圆弧 —— 给 Windows / Material 套上平滑圆角反而不像了
        assertEquals(SurfaceCraft.Card, Styles.of(ThemeStyle.Miuix).surface.craft)
        listOf(
            ThemeStyle.WindowGlass, ThemeStyle.Translucent,
            ThemeStyle.FlatBlur, ThemeStyle.Material,
        ).forEach {
            assertNotEquals(SurfaceCraft.Card, Styles.of(it).surface.craft)
        }
    }

    @Test
    fun `wallpaper scrim grows with transparency`() {
        // 越透的风格，壁纸越需要压暗，否则文字压在花壁纸上没法读
        val acrylic = Styles.of(ThemeStyle.WindowGlass).wallpaperScrim
        val glass = Styles.of(ThemeStyle.Translucent).wallpaperScrim
        assertTrue(glass > acrylic)
        ThemeStyle.entries.forEach {
            val v = Styles.of(it).wallpaperScrim
            assertTrue("$it 的遮罩要在 0..0.8 内", v in 0f..0.8f)
        }
    }

    // ---- 1.4.0 的可选项与动效性格 ----

    @Test
    fun `plasma motion softens timing but keeps the style's spring`() {
        val standard = Styles.of(ThemeStyle.WindowGlass).motion
        val plasma = standard.asPlasma()

        // 时长更长：Kirigami 的 long/veryLong（200/400ms）对上原来的 120/200/320
        assertTrue("Plasma 的时长应当更长", plasma.base > standard.base)
        assertTrue("Plasma 的慢档应当更长", plasma.slow > standard.slow)
        // 曲线换成 Qt 的 OutCubic / InCubic
        assertEquals(JmEasing.outCubic, plasma.enter)
        assertEquals(JmEasing.inCubic, plasma.exit)

        // 弹性是**风格**的属性（HyperOS 的按下回弹），不该被动效性格覆盖掉：
        // Plasma 只改曲线与时长，springy 原样带过来
        assertEquals(standard.springy, plasma.springy)
        assertEquals(
            Styles.of(ThemeStyle.Miuix).motion.springy,
            Styles.of(ThemeStyle.Miuix).motion.asPlasma().springy,
        )
    }

    @Test
    fun `option defaults preserve the existing look`() {
        // 这五个都是「可选」功能：默认必须什么都不改，否则升级就动了别人的界面
        val d = UiOptions()
        assertFalse("悬浮底栏默认关", d.floatingBottomBar)
        assertFalse("莫奈套用到模糊默认关", d.monetBlur)
        assertFalse("通透模式默认关", d.ultraTranslucent)
        assertFalse("预测性返回默认关", d.predictiveBack)
        assertEquals(MotionStyle.Standard, d.motionStyle)
        assertEquals(MotionStyle.Standard, MotionStyle.Default)

        // 持久化的脏值不该让应用起不来（与 ThemeStyle.fromName 同样的约定）
        assertEquals(MotionStyle.Default, MotionStyle.fromName(null))
        assertEquals(MotionStyle.Default, MotionStyle.fromName("Plasmaa"))
        assertEquals(MotionStyle.Plasma, MotionStyle.fromName("Plasma"))
    }

    @Test
    fun `monet tints only exist where a dynamic scheme exists`() {
        // 「莫奈取色套用在模糊上」依赖调色板里的三个色相。
        // 固定色板没有它 —— 所以那个开关在没开动态取色时自然不生效，
        // 这是设计而不是漏做（否则会变成「打开没反应」的假开关）。
        listOf(true, false).forEach { dark ->
            assertTrue(
                "M3 基线配色必须带莫奈色相",
                baselineM3Palette(dark).monetTints.size >= 3,
            )
            assertEquals(
                "固定色板不该假装有莫奈取色",
                0,
                paletteFor(ThemeStyle.WindowGlass, dark).monetTints.size,
            )
            assertEquals(
                "固定色板不该假装有莫奈取色",
                0,
                paletteFor(ThemeStyle.Miuix, dark).monetTints.size,
            )
        }
    }

    // ---- 1.4.2 的动效组织方式：动画由「触发前位置 → 触发后位置」决定 ----

    @Test
    fun `shared key is deterministic because a mismatch fails silently`() {
        // 共享元素两边必须用**同一个键**。键不一致时不会报错 —— 只是没有动画，
        // 属于最难发现的一类失败。所以把键的生成收成一个函数并在这里钉住。
        assertEquals(jmComicSharedKey("123"), jmComicSharedKey("123"))
        assertNotEquals(jmComicSharedKey("123"), jmComicSharedKey("124"))
        // 前缀固定：改掉它等于把所有共享转场静默关掉
        assertTrue(jmComicSharedKey("123").startsWith("jm-cover-"))
    }

    @Test
    fun `hyperos motion is slower and longer-tailed than standard`() {
        val standard = Styles.of(ThemeStyle.WindowGlass).motion
        val hyper = standard.asHyperOS()
        // 层次变化要看得清，时长必须比标准更长
        assertTrue("HyperOS 的转场应当更长", hyper.base > standard.base)
        assertTrue("HyperOS 的收尾应当更长", hyper.slow > standard.slow)
        // 进入用长尾曲线、退出用更快的曲线 —— 两者不能是同一根
        assertEquals(JmEasing.hyperOS, hyper.enter)
        assertEquals(JmEasing.hyperOSOut, hyper.exit)
        // 弹性仍属于风格，不被动效性格覆盖
        assertEquals(standard.springy, hyper.springy)
    }
}
