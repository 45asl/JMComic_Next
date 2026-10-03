package com.jmcomic_next.lyqs.data

import com.jmcomic_next.lyqs.data.prefs.KeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 从收藏里统计"我偏好哪些标签"（1.5.6）。
 *
 * ## 为什么要缓存
 *
 * **列表接口不下发标签**，所以统计一次必须逐本读详情（1.5.1 的屏蔽功能也是因为这个才"逐条读详情"）。
 * 收藏有几十本时那就是几十个请求 —— 每进一次随机页都做一遍是不可接受的，
 * 所以结果落在本地，并且带时间戳：默认**一周内不重扫**。
 *
 * ## 为什么限制本数
 *
 * 扫描上限 [DEFAULT_MAX_WORKS] 本。收藏几百本的用户不该因为一次随机而等几百个请求；
 * 而"偏好哪些标签"这种统计，前 60 本已经足够稳定。
 */
class FavoriteTags(private val sp: KeyValueStore) {
    private val json = Json { ignoreUnknownKeys = true }

    /** 缓存的统计结果（可能为空 = 还没扫过）。 */
    fun cached(): Map<String, Int> = decode(sp.getString(KEY_FAVORITE_TAGS, null)).counts

    /** 缓存时间；0 表示没有缓存。 */
    fun cachedAt(): Long = decode(sp.getString(KEY_FAVORITE_TAGS, null)).at

    /**
     * 扫一遍收藏并写入缓存。
     *
     * 逐本读详情时**限制并发**：这些请求是突发的一批，不限制会给服务端和自己都添麻烦。
     * 单本失败只是少一条样本，不中断整次扫描 —— 统计值而已，不值得为一条失败全部重来。
     */
    suspend fun refresh(
        repo: JmRepository,
        maxWorks: Int = DEFAULT_MAX_WORKS,
        maxParallel: Int = DEFAULT_MAX_PARALLEL,
    ): Map<String, Int> = withContext(Dispatchers.IO) {
        val ids = runCatching {
            val first = repo.favorites(page = 1)
            // 用 totalCount 而不是 list.size：服务端不给总数时它是 0（未知），
            // 拿本页条数冒充总数会在"刚好等于总数"时永远停在第一页
            val pages = if (first.totalCount > first.list.size) 2 else 1
            val rest = if (pages == 1) emptyList() else runCatching { repo.favorites(page = 2).list }.getOrDefault(emptyList())
            (first.list + rest).map { it.id }.distinct().take(maxWorks)
        }.getOrDefault(emptyList())
        if (ids.isEmpty()) return@withContext cached()

        val gate = Semaphore(maxParallel)
        val tagsPerWork: List<Set<String>> = coroutineScope {
            ids.map { id ->
                async {
                    gate.withPermit {
                        runCatching { repo.album(id).tags.toSet() }.getOrDefault(emptySet())
                    }
                }
            }.awaitAll()
        }.filter { it.isNotEmpty() }

        val counts = RandomRanking.favoriteTags(tagsPerWork)
        sp.putString(
            KEY_FAVORITE_TAGS,
            json.encodeToString(
                FavoriteTagCache.serializer(),
                FavoriteTagCache(at = System.currentTimeMillis(), counts = counts),
            ),
        )
        counts
    }

    companion object {
        /** 键名沿用 AppPrefs 里原来的取值，保证升级后旧缓存仍能被读到。 */
        private const val KEY_FAVORITE_TAGS = "favorite_tags_v1"

        const val DEFAULT_MAX_WORKS = 60
        const val DEFAULT_MAX_PARALLEL = 3

        /** 缓存的有效期：一周。 */
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        /**
         * 缓存还算不算新鲜。
         *
         * 抽成静态纯函数就是为了能直接测边界：没有缓存（at=0）永远算不新鲜，
         * 而"刚好到期"按时效处理 —— 差一毫秒不算新鲜的判断，写反了会变成永远不刷新。
         */
        fun isFresh(at: Long, now: Long, maxAge: Long = MAX_AGE_MS): Boolean =
            at > 0 && now - at < maxAge

        fun decode(raw: String?): FavoriteTagCache =
            raw?.let { runCatching { Format.decodeFromString(FavoriteTagCache.serializer(), it) }.getOrNull() }
                ?: FavoriteTagCache()

        // Json 实例**建一次**就够：编译器直接点名"每次使用都创建会很慢"
        private val Format = Json { ignoreUnknownKeys = true }
    }
}

/** 缓存内容：统计时间 + 标签计数。 */
@Serializable
data class FavoriteTagCache(
    val at: Long = 0,
    val counts: Map<String, Int> = emptyMap(),
)
