package com.jmcomic_next.desktop

import com.jmcomic_next.lyqs.data.prefs.KeyValueStore
import java.util.prefs.Preferences

/**
 * [KeyValueStore] 的桌面实现，背后是 java.util.prefs。
 *
 * 选它的原因：JDK 自带、Linux 上落在 ~/.java/.userPrefs（Windows 上落注册表），
 * 不需要自己管文件路径与并发写入。与 Android 的 SharedPreferences 一样是
 * 「同一个名字的实例在进程内共用同一份数据」。
 */
class PreferencesKeyValueStore(node: String) : KeyValueStore {

    private val prefs: Preferences = Preferences.userRoot().node("com/jmcomic_next/$node")

    override fun getString(key: String, def: String?): String? = prefs.get(key, def)

    override fun putString(key: String, value: String?) {
        if (value == null) prefs.remove(key) else prefs.put(key, value)
    }

    override fun getBoolean(key: String, def: Boolean): Boolean = prefs.getBoolean(key, def)

    override fun putBoolean(key: String, value: Boolean) = prefs.putBoolean(key, value)

    override fun getInt(key: String, def: Int): Int = prefs.getInt(key, def)

    override fun putInt(key: String, value: Int) = prefs.putInt(key, value)

    override fun getLong(key: String, def: Long): Long = prefs.getLong(key, def)

    override fun putLong(key: String, value: Long) = prefs.putLong(key, value)

    override fun remove(key: String) = prefs.remove(key)

    override fun clear() = prefs.clear()
}
