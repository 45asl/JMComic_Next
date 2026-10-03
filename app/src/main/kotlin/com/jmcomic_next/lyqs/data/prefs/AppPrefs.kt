package com.jmcomic_next.lyqs.data.prefs

import android.content.Context
import androidx.core.content.edit
import com.jmcomic_next.lyqs.ui.theme.ThemeStyle
import com.jmcomic_next.lyqs.ui.theme.MotionStyle
import com.jmcomic_next.lyqs.ui.UiOptions
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
     * 五套风格见 [ThemeStyle]：它们换的不只是配色，还有圆角、表面工艺、字重与动效。
     */
    var themeStyle: ThemeStyle
        get() = ThemeStyle.fromName(sp.getString(KEY_STYLE, null))
        set(value) = sp.edit { putString(KEY_STYLE, value.name) }

    /** 是否启用 Material You 动态取色。默认关闭，理由见 JmTheme 的注释。 */
    var dynamicColor: Boolean
        get() = sp.getBoolean(KEY_DYNAMIC, false)
        set(value) = sp.edit { putBoolean(KEY_DYNAMIC, value) }

    // ---- 1.4.0 的五个可选项。默认全部关闭 / 取标准档，升级不动任何人的界面 ----

    /** 悬浮底栏：底栏浮在内容之上（胶囊形），而不是贴底的一条。 */
    var floatingBottomBar: Boolean
        get() = sp.getBoolean(KEY_FLOATING_BAR, false)
        set(value) = sp.edit { putBoolean(KEY_FLOATING_BAR, value) }

    /** 莫奈取色套用到模糊：用动态取色派生的色相给模糊层上色。 */
    var monetBlur: Boolean
        get() = sp.getBoolean(KEY_MONET_BLUR, false)
        set(value) = sp.edit { putBoolean(KEY_MONET_BLUR, value) }

    /** 通透模式：玻璃不覆盖底色、只留模糊；同时打开文字阴影保证可读。 */
    var ultraTranslucent: Boolean
        get() = sp.getBoolean(KEY_ULTRA_TRANSLUCENT, false)
        set(value) = sp.edit { putBoolean(KEY_ULTRA_TRANSLUCENT, value) }

    /** 预测性返回手势（Android 13+）。 */
    var predictiveBack: Boolean
        get() = sp.getBoolean(KEY_PREDICTIVE_BACK, false)
        set(value) = sp.edit { putBoolean(KEY_PREDICTIVE_BACK, value) }

    /**
     * 连载更新提醒（1.5.3）：**可选、默认关**。
     *
     * 打开后每半天左右让系统挑个合适时机检查一次"你追的有没有更新"，
     * 有变化才发一条通知（指纹去重，见 [com.jmcomic_next.lyqs.data.SerialNotify]）。
     */
    var serialNotify: Boolean
        get() = sp.getBoolean(KEY_SERIAL_NOTIFY, false)
        set(value) = sp.edit { putBoolean(KEY_SERIAL_NOTIFY, value) }

    /** 上次通知过的那批更新的指纹；用来避免同一条更新被反复通知。 */
    var serialNotifySeen: String?
        get() = sp.getString(KEY_SERIAL_NOTIFY_SEEN, null)
        set(value) = sp.edit { putString(KEY_SERIAL_NOTIFY_SEEN, value) }

    /** 动效性格：标准 / Plasma。 */
    var motionStyle: MotionStyle
        get() = MotionStyle.fromName(sp.getString(KEY_MOTION_STYLE, null))
        set(value) = sp.edit { putString(KEY_MOTION_STYLE, value.name) }

    /**
     * 五个可选项作为一个整体读写。
     *
     * 这样设置页只需要一个 `UiOptions + onUiOptionsChange`，而不是五个开关各配一对回调；
     * 也让「新增一个开关」不必再改一遍四处函数签名。
     */
    var uiOptions: UiOptions
        get() = UiOptions(
            floatingBottomBar = floatingBottomBar,
            monetBlur = monetBlur,
            ultraTranslucent = ultraTranslucent,
            predictiveBack = predictiveBack,
            motionStyle = motionStyle,
        )
        set(value) {
            floatingBottomBar = value.floatingBottomBar
            monetBlur = value.monetBlur
            ultraTranslucent = value.ultraTranslucent
            predictiveBack = value.predictiveBack
            motionStyle = value.motionStyle
        }

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
        const val KEY_FLOATING_BAR = "floating_bottom_bar"
        const val KEY_MONET_BLUR = "monet_blur"
        const val KEY_ULTRA_TRANSLUCENT = "ultra_translucent"
        const val KEY_PREDICTIVE_BACK = "predictive_back"
        const val KEY_MOTION_STYLE = "motion_style"
        const val KEY_SERIAL_NOTIFY = "serial_notify"
        const val KEY_SERIAL_NOTIFY_SEEN = "serial_notify_seen"
        const val KEY_READER_MODE = "reader_mode"
        const val KEY_SEARCH_HISTORY = "search_history"
        const val SEARCH_HISTORY_LIMIT = 20
    }
}
