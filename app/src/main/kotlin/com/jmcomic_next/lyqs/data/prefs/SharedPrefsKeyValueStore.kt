package com.jmcomic_next.lyqs.data.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * [KeyValueStore] 的 Android 实现 —— 背后就是 SharedPreferences。
 *
 * 写入用 `apply()`（异步落盘），与原先各处 `edit { }` 的行为一致。
 */
class SharedPrefsKeyValueStore(context: Context, name: String) : KeyValueStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getString(key: String, def: String?): String? = prefs.getString(key, def)

    override fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getBoolean(key: String, def: Boolean): Boolean = prefs.getBoolean(key, def)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getInt(key: String, def: Int): Int = prefs.getInt(key, def)

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun getLong(key: String, def: Long): Long = prefs.getLong(key, def)

    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
