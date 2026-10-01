package com.jmcomic_next.lyqs.data.prefs

import android.content.Context
import androidx.core.content.edit

/** 主题模式。默认跟随系统 —— 与博客首次访问的行为一致。 */
enum class ThemeMode { System, Light, Dark }

/**
 * 阅读器的浏览形态。
 *
 * 两种都在官方 Web 端存在：默认是纵向连续流，另有一个 Swiper 横向翻页模式
 * （`Read.tsx` 里的 `SwiperSlide` + `onSlideChange`）。长条页漫画更适合连续滚动，
 * 单页构图的作品更适合横向翻页，因此做成用户可切换而不是替他决定。
 */
enum class ReaderMode {
    /** 纵向连续滚动。 */
    Scroll,

    /** 横向逐页翻动，每页适配整屏。 */
    Page,
}

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

    /** 阅读器浏览形态，默认纵向连续滚动。 */
    var readerMode: ReaderMode
        get() = runCatching { ReaderMode.valueOf(sp.getString(KEY_READER_MODE, null) ?: "") }
            .getOrDefault(ReaderMode.Scroll)
        set(value) = sp.edit { putString(KEY_READER_MODE, value.name) }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_READER_MODE = "reader_mode"
    }
}
