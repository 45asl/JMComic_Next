package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.ui.theme.RadiusScale
import com.jmcomic_next.lyqs.ui.theme.Styles
import com.jmcomic_next.lyqs.ui.theme.SurfaceCraft
import com.jmcomic_next.lyqs.ui.theme.ThemeStyle
import com.jmcomic_next.lyqs.ui.theme.paletteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 四套风格的令牌。
 *
 * 这些断言看起来「只是查表」，但它们钉住的是一件事：**四套风格必须真的不一样**。
 * 风格系统最容易出的问题不是崩溃，而是「做了四个选项，看起来却差不多」——
 * 圆角抄成同一组、表面工艺都写成 Acrylic、字重没换，用户选了半天没感觉。
 * 所以这里逐项要求彼此不同，而不是只测「有四个枚举值」。
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
    }

    @Test
    fun `each style draws surfaces its own way`() {
        val crafts = ThemeStyle.entries.map { Styles.of(it).surface.craft }
        assertEquals(crafts.size, crafts.toSet().size)
        assertEquals(SurfaceCraft.Card, Styles.of(ThemeStyle.Miuix).surface.craft)
        assertEquals(SurfaceCraft.Tonal, Styles.of(ThemeStyle.Material).surface.craft)
        assertEquals(SurfaceCraft.Glass, Styles.of(ThemeStyle.Translucent).surface.craft)
    }

    @Test
    fun `translucent is actually more transparent than window glass`() {
        // 这两个风格同出 Windhawk 的窗口玻璃一族，差别就该体现在「透多少」上
        val acrylic = Styles.of(ThemeStyle.WindowGlass).surface
        val glass = Styles.of(ThemeStyle.Translucent).surface
        assertTrue("Translucent 的填充必须更透", glass.fillAlphaScale < acrylic.fillAlphaScale)
        assertTrue("Translucent 要铺强调色染", glass.accentTint > acrylic.accentTint)
        assertTrue("Translucent 的模糊要更强", glass.blur > acrylic.blur)
    }

    @Test
    fun `solid styles do not use glass or borders`() {
        // Miuix / Material 是实心体系：既没有描边，也没有模糊与颗粒。
        // 留下任何一项都会让它看起来像「换了颜色的玻璃」，那就白做了
        listOf(ThemeStyle.Miuix, ThemeStyle.Material).forEach { style ->
            val s = Styles.of(style).surface
            assertEquals("$style 不该画描边", 0f, s.hairline.value, 0.001f)
            assertEquals("$style 不该用模糊", 0f, s.blur.value, 0.001f)
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
    fun `material keeps its cards flat`() {
        // haka_comic 的卡片一律 elevation: 0，靠色调分层；这里同样不能有投影，
        // 否则 Material 风格会开始像「带阴影的玻璃」，那是最四不像的一种
        val m = Styles.of(ThemeStyle.Material).surface
        assertEquals(0f, m.shadowOf(0).value, 0.001f)
        assertEquals(0f, m.shadowOf(1).value, 0.001f)
        assertEquals(12f, Styles.of(ThemeStyle.Material).radius.lg.value, 0.001f)
    }

    @Test
    fun `only miuix uses continuous corners`() {
        // HyperOS 的圆角是「平滑圆角」（连续曲率），不是四分之一圆弧；
        // 另外三套保持圆弧 —— 给 Windows / Material 套上平滑圆角反而不像了
        assertEquals(SurfaceCraft.Card, Styles.of(ThemeStyle.Miuix).surface.craft)
        listOf(ThemeStyle.WindowGlass, ThemeStyle.Translucent, ThemeStyle.Material).forEach {
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
}
