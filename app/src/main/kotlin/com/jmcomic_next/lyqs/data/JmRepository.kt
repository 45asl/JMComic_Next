package com.jmcomic_next.lyqs.data

import com.jmcomic_next.lyqs.data.crypto.JmCrypto
import com.jmcomic_next.lyqs.data.remote.Envelope
import com.jmcomic_next.lyqs.data.remote.JmException
import com.jmcomic_next.lyqs.data.remote.JmHostDiscovery
import com.jmcomic_next.lyqs.data.remote.JmJson
import com.jmcomic_next.lyqs.data.remote.JmPaths
import com.jmcomic_next.lyqs.data.remote.JmRemote
import com.jmcomic_next.lyqs.data.remote.JmSession
import com.jmcomic_next.lyqs.data.remote.dto.AlbumDetail
import com.jmcomic_next.lyqs.data.remote.dto.JmSettings
import com.jmcomic_next.lyqs.data.remote.dto.ListItem
import com.jmcomic_next.lyqs.data.remote.dto.PagedList
import com.jmcomic_next.lyqs.data.remote.dto.PromoteSection
import com.jmcomic_next.lyqs.data.remote.dto.ReadPayload
import com.jmcomic_next.lyqs.data.remote.dto.SearchPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * 业务数据入口。
 *
 * 承担三件事：
 *  1. **引导**（[bootstrap]）：发现 API 主机 → 拉取 `setting` 拿到图床主机
 *  2. 把接口的两种 `data` 形态（纯数组 / `{list,total}`）归一成 [PagedList]
 *  3. 拼装封面图地址
 *
 * 不做缓存：列表数据量小、刷新频繁，缓存带来的失效问题比收益大。
 * 需要跨页面复用的只有 [bootstrap] 的结果，而它本就存在 [JmSession] 里。
 */
class JmRepository(private val remote: JmRemote) {

    private val session: JmSession get() = remote.session

    /** 保证引导只跑一次的互斥锁 —— 多个页面同时进入时不应重复发现主机。 */
    private val bootstrapLock = Mutex()
    private var bootstrapped = false

    /**
     * 引导：发现主机 → 取配置。
     *
     * @return 是否就绪。已在会话内成功引导过则直接返回 true。
     * @throws JmException 主机发现全部失败时
     */
    suspend fun bootstrap(): Boolean = bootstrapLock.withLock {
        if (bootstrapped && session.isReady) return@withLock true

        if (!session.isReady) {
            val host = JmHostDiscovery.discover(remote.okHttp, session)
                ?: throw JmException(
                    "无法连接到服务端：主机发现全部失败",
                    JmException.Kind.Network,
                )
        }

        // 配置里带图床主机；失败不应阻断主流程（封面会回退到 API 主机）
        runCatching { settings() }
            .onSuccess { session.imageHost = it.imgHost?.takeIf(String::isNotBlank) }

        bootstrapped = true
        true
    }

    /** 应用配置。 */
    suspend fun settings(): JmSettings = remote.get(
        JmPaths.SETTING,
        JmSettings.serializer(),
        mapOf(
            "app_img_shunt" to "1",
            "lang" to "zh",
            "t" to (System.currentTimeMillis() / 1000L).toString(),
        ),
    )

    /**
     * 首页主推荐。
     *
     * 返回的是**分区**列表（每个分区带标题和一串漫画），不是扁平列表 ——
     * 依据 `InterFace.ts` 的 `PromoteResponse`。
     */
    suspend fun promote(): List<PromoteSection> = remote.get(
        JmPaths.PROMOTE,
        ListSerializer(PromoteSection.serializer()),
    )

    /**
     * 首页最新，分页。
     *
     * `data` 目前是裸数组，服务端补上 total 后会变成 `{list,total}` —— 两种都接受
     * （源码 `mainReducer.ts` 对 `latest` 正是这么处理的）。
     */
    suspend fun latest(page: Int): PagedList = remote.get(
        JmPaths.LATEST,
        JsonElement.serializer(),
        mapOf("page" to page.toString()),
    ).let { el ->
        when (el) {
            is JsonArray -> PagedList(
                items = JmJson.decodeFromJsonElement(ListSerializer(ListItem.serializer()), el),
            )
            is JsonObject -> PagedList(
                items = el["list"]?.takeIf { it is JsonArray }?.let {
                    JmJson.decodeFromJsonElement(ListSerializer(ListItem.serializer()), it)
                } ?: emptyList(),
                total = el["total"].asIntOrZero(),
            )
            else -> PagedList()
        }
    }

    /**
     * 搜索。
     *
     * @param query 关键词
     * @param order `o` 参数，排序方式，留空用服务端默认
     * @param type `search_type`，服务端的检索类型
     */
    suspend fun search(
        query: String,
        page: Int = 1,
        order: String? = null,
        type: String? = null,
    ): PagedList {
        val payload = remote.get(
            JmPaths.SEARCH,
            SearchPayload.serializer(),
            buildMap {
                put("search_query", query)
                put("page", page.toString())
                order?.let { put("o", it) }
                type?.let { put("search_type", it) }
            },
        )
        // 搜索的 total 是字符串，而 latest 的是数字 —— 各按各的形态取，统一成 Int
        return PagedList(payload.content, payload.total?.toIntOrNull() ?: 0)
    }

    /** 漫画详情。 */
    suspend fun album(id: String): AlbumDetail = remote.get(
        JmPaths.ALBUM,
        AlbumDetail.serializer(),
        mapOf("id" to id),
    )

    /**
     * 阅读内容（图片列表）。
     *
     * @param id 章节 id，同时也是反切片算法需要的 `aid`
     * @param express 服务端预留的加速/线路参数，留空即默认线路
     */
    suspend fun read(id: String, express: String? = null): ReadPayload = remote.get(
        JmPaths.COMIC_READ,
        ReadPayload.serializer(),
        buildMap {
            put("id", id)
            express?.let { put("express", it) }
        },
    )

    /**
     * 某个列表项的封面地址。
     *
     * 优先用服务端下发的 `image`（可能是相对路径，需补图床主机）；
     * 没有才退回按 id 拼模板 —— 两条路径都是线上真实存在的形态。
     */
    fun coverUrl(item: ListItem): String {
        val raw = item.image?.takeIf { it.isNotBlank() }
        if (raw != null) {
            return if (raw.startsWith("http")) raw else session.imageUrl(raw)
        }
        return coverUrl(item.id, item.updateAt)
    }

    /**
     * 按 id 拼封面地址（回退路径）。
     *
     * 封面在 `InterFace.ts` 里虽然是接口字段，但实际常有缺失，
     * 而 `ComicList.tsx` 用的是 `${img_host}/media/albums/${id}_3x4.jpg?v=${update_at}`
     * 这条约定；`update_at` 作为版本号参与 URL，让客户端缓存自然失效。
     */
    fun coverUrl(id: String, updateAt: String? = null): String {
        val path = JmPaths.COVER_TEMPLATE.format(id)
        val base = session.imageUrl(path)
        return if (updateAt.isNullOrBlank()) base else "$base?v=$updateAt"
    }

    /**
     * 判断某张图片是否需要做切片还原。
     *
     * GIF 不切；`aid` 小于 `scramble_id` 的老漫画也不切 —— 两条规则都来自
     * 源码 `scramble_image` 的入口判断。
     */
    fun needsUnscramble(imageUrl: String, aid: Int, scrambleId: Int): Boolean =
        JmCrypto.needsUnscramble(imageUrl, aid, scrambleId)

    /** 宽容地把 `total` 读成 Int：服务端有时给字符串、有时给数字、有时干脆不给。 */
    private fun JsonElement?.asIntOrZero(): Int =
        this?.let { runCatching { it.jsonPrimitive.content.toInt() }.getOrNull() } ?: 0

    companion object {
        /** 便捷构造，供 App 级容器使用。 */
        fun create(session: JmSession = JmSession()): JmRepository =
            JmRepository(JmRemote(session))
    }
}
