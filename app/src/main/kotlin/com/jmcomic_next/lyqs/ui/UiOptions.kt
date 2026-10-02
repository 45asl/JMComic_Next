package com.jmcomic_next.lyqs.ui

import androidx.compose.runtime.staticCompositionLocalOf
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
