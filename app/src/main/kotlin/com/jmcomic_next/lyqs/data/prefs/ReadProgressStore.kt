package com.jmcomic_next.lyqs.data.prefs

import android.content.Context
import androidx.core.content.edit
import com.jmcomic_next.lyqs.data.remote.JmJson
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * 阅读进度。
 *
 * 「读到哪一话」是**纯本地**信息：服务端的观看历史只记录到作品粒度，
 * 并不告诉我们上次停在哪一话。官方客户端也是放在本地
 * （`Read.tsx` 里把 `{comicId: [chapterId...]}` 存进 localStorage 的 `read` 键）。
 *
 * 存储形态是一个 `作品 id → 章节 id` 的映射，整体序列化成 JSON。
 * 用 SharedPreferences 而非数据库：条目数等于用户读过的作品数，量级很小，
 * 而且读取发生在详情页展示时，需要的是同步、无 IO 等待的取值。
 *
 * 没有做条数上限。真要清理时，用户在应用设置里清数据即可 ——
 * 引入 LRU 会让「我读过的作品突然不记得了」这种困惑出现，代价大于收益。
 *
 * 实例本身很轻：多创建几个也无妨，它们的读写都落在同一个 SharedPreferences 文件上
 * （Android 对同一名字的 SharedPreferences 在进程内是复用的），因此各页面各自持有一个即可，
 * 不必为此引入全局单例或 CompositionLocal。
 */
class ReadProgressStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val serializer = MapSerializer(String.serializer(), String.serializer())

    /** 记录某作品读到哪一话。 */
    fun record(comicId: String, chapterId: String) {
        if (comicId.isBlank() || chapterId.isBlank()) return
        val map = readAll().toMutableMap()
        // 值没变就不写盘：详情页每次展示都会调用一次，避免无意义的重写
        if (map[comicId] == chapterId) return
        map[comicId] = chapterId
        prefs.edit { putString(KEY_MAP, JmJson.encodeToString(serializer, map)) }
    }

    /** 上次读到的那一话；没有记录时返回 null。 */
    fun lastChapterId(comicId: String): String? =
        readAll()[comicId]?.takeIf { it.isNotBlank() }

    fun clear() = prefs.edit { remove(KEY_MAP) }

    private fun readAll(): Map<String, String> {
        val raw = prefs.getString(KEY_MAP, null) ?: return emptyMap()
        // 解析失败时当作没有记录，而不是抛异常 —— 这份数据是可再生的，不值得因此崩溃
        return runCatching { JmJson.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())
    }

    private companion object {
        const val PREFS_NAME = "jm_read_progress"
        const val KEY_MAP = "progress"
    }
}
