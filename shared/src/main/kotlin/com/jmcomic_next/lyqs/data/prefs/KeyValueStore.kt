package com.jmcomic_next.lyqs.data.prefs

/**
 * 极简键值存储（2.0.0 起）。
 *
 * 成员按各存储类的**真实用量**定：目前只用到字符串与布尔的读写、以及删除。
 * Android 侧由 SharedPreferences 实现，桌面侧由 java.util.prefs 或文件实现。
 *
 * 为什么用这么小的接口而不是直接依赖 SharedPreferences：
 * [AppPrefs]、[ReadProgressStore]、[BlockStore] 三个类里其实**没有任何平台逻辑** ——
 * 它们只是拿 SharedPreferences 当键值存储用（键名、默认值、JSON 编解码都是跨平台的）。
 * 把这个接口抽出来之后，这三个类就能整体搬进跨平台模块，两个平台共用同一份实现，
 * 而不是各写一套 —— 后者迟早会分叉。
 */
interface KeyValueStore {
    fun getString(key: String, def: String? = null): String?
    fun putString(key: String, value: String?)

    fun getBoolean(key: String, def: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)

    fun getInt(key: String, def: Int = 0): Int
    fun putInt(key: String, value: Int)

    fun getLong(key: String, def: Long = 0L): Long
    fun putLong(key: String, value: Long)

    fun remove(key: String)
}
