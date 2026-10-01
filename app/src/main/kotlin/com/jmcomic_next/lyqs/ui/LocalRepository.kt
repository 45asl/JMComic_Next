package com.jmcomic_next.lyqs.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.jmcomic_next.lyqs.data.JmRepository

/**
 * 向下传递仓储实例。
 *
 * 与 [com.jmcomic_next.lyqs.ui.theme.LocalJmPalette] 一样，
 * 拿不到时直接抛异常而不是返回空实现 —— 配置遗漏应该在开发期立刻暴露。
 */
val LocalRepository = staticCompositionLocalOf<JmRepository> {
    error("JmRepository 尚未提供：请在 MainActivity 里用 CompositionLocalProvider 注入")
}
