package com.jmcomic_next.lyqs.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.jmcomic_next.lyqs.data.TagBlockResolver

/**
 * 列表标签屏蔽的解析器（1.5.1）。
 *
 * 默认 null：拿不到时列表照常显示，只是不做标签过滤 —— 而不是崩掉或什么都不显示。
 * 实例由 [com.jmcomic_next.lyqs.JmApp] 持有（它要活过单个页面，缓存才有意义）。
 */
val LocalTagBlocker = staticCompositionLocalOf<TagBlockResolver?> { null }
