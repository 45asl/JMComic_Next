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
    private val timeSerializer = MapSerializer(String.serializer(), Long.serializer())

    /**
     * 记录某作品读到哪一话，**并记下这一次阅读的时间**。
     *
     * 时间是给「连载更新」判定用的：列表项自带 `update_at`，与"我最后读它的时间"一比
     * 就知道有没有更新 —— 这条路径**不需要额外请求**，也不需要章节目录。
     * 单独存一个 map（而不是把值改成复合结构）是为了**兼容已有数据**：
     * 老用户的 `progress` 原样可读，只是"时间"一开始为空。
     */
    fun record(comicId: String, chapterId: String, at: Long = System.currentTimeMillis()) {
        if (comicId.isBlank() || chapterId.isBlank()) return
        val map = readAll().toMutableMap()
        val times = readTimes().toMutableMap()
        val chapterChanged = map[comicId] != chapterId
        val timeChanged = times[comicId] != at
        if (!chapterChanged && !timeChanged) return
        if (chapterChanged) map[comicId] = chapterId
        if (timeChanged) times[comicId] = at
        prefs.edit {
            if (chapterChanged) putString(KEY_MAP, JmJson.encodeToString(serializer, map))
            if (timeChanged) putString(KEY_TIME, JmJson.encodeToString(timeSerializer, times))
        }
    }

    /** 上次读到的那一话；没有记录时返回 null。 */
    fun lastChapterId(comicId: String): String? =
        readAll()[comicId]?.takeIf { it.isNotBlank() }

    /** 上次读它的时间（epoch 毫秒）；从未记录过返回 null —— 也就是"没追过"。 */
    fun lastReadAt(comicId: String): Long? = readTimes()[comicId]

    /** 读过（追过）的作品 id 集合。 */
    fun trackedComicIds(): Set<String> = readTimes().keys

    fun clear() = prefs.edit { remove(KEY_MAP); remove(KEY_TIME) }

    private fun readAll(): Map<String, String> {
        val raw = prefs.getString(KEY_MAP, null) ?: return emptyMap()
        // 解析失败时当作没有记录，而不是抛异常 —— 这份数据是可再生的，不值得因此崩溃
        return runCatching { JmJson.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())
    }

    private fun readTimes(): Map<String, Long> {
        val raw = prefs.getString(KEY_TIME, null) ?: return emptyMap()
        return runCatching { JmJson.decodeFromString(timeSerializer, raw) }.getOrDefault(emptyMap())
    }

    private companion object {
        const val PREFS_NAME = "jm_read_progress"
        const val KEY_MAP = "progress"
        const val KEY_TIME = "read_time"
    }
}
