package com.jmcomic_next.lyqs.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import com.jmcomic_next.lyqs.ui.theme.MotionStyle

/**
 * 一组**可选外观行为**。
 *
 * 集中成一个 data class，而不是五个散落的参数：这些东西会同时被导航、表面绘制、
 * 环境底、文字排版读到，挨个往下传会变成「每加一个开关就改十个函数签名」。
 *
 * **默认值全部取「不改变现有外观」的那一档** —— 这些功能都是可选项，
 * 升级不该把任何人的界面动掉。
 */
data class UiOptions(
    /** 悬浮底栏：底栏变成浮在内容之上的胶囊，而不是贴底的一条。 */
    val floatingBottomBar: Boolean = false,
    /** 莫奈取色套用到模糊：用动态取色派生的色相给模糊层上色。 */
    val monetBlur: Boolean = false,
    /**
     * 通透模式：玻璃**不再覆盖一层底色**，只留模糊与描边。
     *
     * 代价是文字直接压在壁纸上，所以这一档必须同时开文字描边/阴影，
     * 否则浅色壁纸上的浅色文字会直接看不见（见 [com.jmcomic_next.lyqs.ui.theme.JmTheme]）。
     */
    val ultraTranslucent: Boolean = false,
    /** 预测性返回手势（Android 13+）：返回时页面跟手退后。 */
    val predictiveBack: Boolean = false,
    /** 动效性格（标准 / Plasma）。 */
    val motionStyle: MotionStyle = MotionStyle.Default,
)

/** 当前生效的可选开关。拿不到时用全默认值 —— 这些开关不该让界面崩掉。 */
val LocalUiOptions = staticCompositionLocalOf { UiOptions() }

/**
 * 底部栏给内容预留的高度（0 = 当前页面没有底栏）。
 *
 * 有底栏时内容**不再被挤出屏幕**，而是从底栏**后面**穿过去 —— 这才是「悬浮」：
 * 胶囊背后有东西在滚动，它的半透明才有意义。此前是把内容整块上移、底栏独占一条空地，
 * 于是「悬浮的东西不悬浮」。
 *
 * 代价落在页面自己身上：列表必须把这段高度加进 `contentPadding`，
 * 否则最后一条会被永久压在胶囊下面（可滚出去的空间不够）。
 * 底栏只在四个主 Tab 出现（见 JmNavHost），所以只需要那四个页面消费它。
 */
val LocalBottomBarInset = staticCompositionLocalOf { 0.dp }
