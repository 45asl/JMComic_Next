package com.jmcomic_next.lyqs.data.prefs

import android.content.Context
import androidx.core.content.edit
import com.jmcomic_next.lyqs.ui.theme.ThemeStyle
import com.jmcomic_next.lyqs.data.remote.JmJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

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

    /**
     * 界面风格。
     *
     * 默认 [ThemeStyle.Default]（WindowGlass）—— 那是本应用原来的样子。
     * 四套风格见 [ThemeStyle]：它们换的不只是配色，还有圆角、表面工艺、字重与动效。
     */
    var themeStyle: ThemeStyle
        get() = ThemeStyle.fromName(sp.getString(KEY_STYLE, null))
        set(value) = sp.edit { putString(KEY_STYLE, value.name) }

    /** 是否启用 Material You 动态取色。默认关闭，理由见 JmTheme 的注释。 */
    var dynamicColor: Boolean
        get() = sp.getBoolean(KEY_DYNAMIC, false)
        set(value) = sp.edit { putBoolean(KEY_DYNAMIC, value) }

    /**
     * 搜索历史，最近的在前。
     *
     * 官方同样把搜索历史放本地（localStorage 的 `search` 键）。这里限制 20 条：
     * 历史是为了快速重搜近期的词，无限增长只会让列表变成需要滚动才能用的负担。
     */
    var searchHistory: List<String>
        get() = runCatching {
            JmJson.decodeFromString(
                historySerializer,
                sp.getString(KEY_SEARCH_HISTORY, null) ?: "[]",
            )
        }.getOrDefault(emptyList())
        set(value) = sp.edit {
            putString(
                KEY_SEARCH_HISTORY,
                JmJson.encodeToString(historySerializer, value.take(SEARCH_HISTORY_LIMIT)),
            )
        }

    /** 记一条搜索词：已存在则提到最前，避免重复项把列表挤满。 */
    fun addSearchHistory(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        searchHistory = listOf(q) + searchHistory.filterNot { it.equals(q, ignoreCase = true) }
    }

    fun clearSearchHistory() {
        searchHistory = emptyList()
    }

    /** 阅读器浏览形态，默认纵向连续滚动。 */
    var readerMode: ReaderMode
        get() = runCatching { ReaderMode.valueOf(sp.getString(KEY_READER_MODE, null) ?: "") }
            .getOrDefault(ReaderMode.Scroll)
        set(value) = sp.edit { putString(KEY_READER_MODE, value.name) }

    private companion object {
        val historySerializer = ListSerializer(String.serializer())

        const val KEY_THEME = "theme_mode"
        const val KEY_STYLE = "theme_style"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_READER_MODE = "reader_mode"
        const val KEY_SEARCH_HISTORY = "search_history"
        const val SEARCH_HISTORY_LIMIT = 20
    }
}
