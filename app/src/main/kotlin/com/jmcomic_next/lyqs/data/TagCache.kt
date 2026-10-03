package com.jmcomic_next.lyqs.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 标签屏蔽的**持久缓存**：按作品 id 记它的标签集合。
 *
 * 为什么必须缓存：列表接口**不返回标签**，标签只能逐条去详情接口取。若不缓存，
 * 用户每滚一次列表就会把同一批作品重新请求一遍 —— 流量与电量的灾难，也会被服务端当滥用。
 *
 * 编解码与淘汰是**纯逻辑**（[load] / [dump]），不依赖 Android 框架，因此可以直接单测；
 * 落盘由调用方读写一个字符串完成（SharedPreferences）。
 */
class TagCache(private val capacity: Int = 400) {

    /**
     * 插入序即淘汰序。**[get] 会把命中的条目移到末尾**（真正的 LRU 触碰），
     * 于是「最久没被用到的」先被淘汰 —— 列表来回滚动时，反复出现的作品会留在缓存里。
     *
     * 全部方法都 `@Synchronized`：这个缓存会被多路并发取详情的协程同时读写
     * （见 [TagBlockResolver] 的 Semaphore），不锁的话 `LinkedHashMap` 会被写坏。
     */
    private val map = LinkedHashMap<String, Set<String>>()

    @Synchronized
    fun get(id: String): Set<String>? {
        val tags = map.remove(id) ?: return null
        map[id] = tags          // 重新插入 = 移到末尾 = LRU 触碰
        return tags
    }

    @Synchronized
    fun put(id: String, tags: Set<String>) {
        map.remove(id)
        map[id] = tags
        while (map.size > capacity) {
            val oldest = map.keys.firstOrNull() ?: break
            map.remove(oldest)
        }
    }

    @Synchronized
    fun all(): Map<String, Set<String>> = LinkedHashMap(map)

    @Synchronized
    fun size(): Int = map.size

    /**
     * 落盘格式用 **JSON**，不要用自定义分隔符。
     *
     * 这里踩过一个很隐蔽的坑：先前用 U+0001 当标签分隔符，而 **XML 1.0 不能表示
     * 这类控制字符** —— 写进 SharedPreferences 时它被吞掉，读回来就分不开了，
     * 每部作品的标签会被粘成**一个**巨型标签。后果是：当次运行内屏蔽正常
     * （内存里是好的），**重启后缓存一读回来就全部失效**，而且完全静默
     * （`equals` 匹配不上而已，没有任何报错）。
     *
     * JSON 由 kotlinx-serialization 负责转义：标签里出现逗号、引号、制表符、控制字符
     * 都不会坏。这个项目本来就在用它，不引入新依赖。
     * 回归测试见 `TagBlockResolverTest` 里那条 "survives tags containing separators"。
     */
    @Synchronized
    fun dump(): String = runCatching {
        JSON.encodeToString(SERIALIZER, map.mapValues { (_, tags) -> tags.toList() })
    }.getOrDefault("{}")

    @Synchronized
    fun load(raw: String?) {
        map.clear()
        if (raw.isNullOrBlank()) return
        val decoded = runCatching { JSON.decodeFromString(SERIALIZER, raw) }.getOrNull() ?: return
        decoded.forEach { (id, tags) -> map[id] = tags.toSet() }
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        val SERIALIZER = MapSerializer(String.serializer(), ListSerializer(String.serializer()))
    }
}
