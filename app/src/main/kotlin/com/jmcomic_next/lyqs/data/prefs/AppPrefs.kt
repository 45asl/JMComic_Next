package com.jmcomic_next.lyqs.data.prefs

import android.content.Context
import androidx.core.content.edit

/** 主题模式。默认跟随系统 —— 与博客首次访问的行为一致。 */
enum class ThemeMode { System, Light, Dark }

/**
 * 本地偏好。
 *
 * 用 SharedPreferences 而不是 DataStore：只有两个键、没有并发写入，
 * 为它引入一个额外的依赖与 Flow 包装并不划算。写操作都是 apply()（异步落盘）。
 */
class AppPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("jm_prefs", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(sp.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.System)
        set(value) = sp.edit { putString(KEY_THEME, value.name) }

    /** 是否启用 Material You 动态取色。默认关闭，理由见 JmTheme 的注释。 */
    var dynamicColor: Boolean
        get() = sp.getBoolean(KEY_DYNAMIC, false)
        set(value) = sp.edit { putBoolean(KEY_DYNAMIC, value) }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
    }
}
