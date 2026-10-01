package com.jmcomic_next.lyqs.data

import com.jmcomic_next.lyqs.data.crypto.JmCrypto
import com.jmcomic_next.lyqs.data.remote.Envelope
import com.jmcomic_next.lyqs.data.remote.JmException
import com.jmcomic_next.lyqs.data.remote.JmHostDiscovery
import com.jmcomic_next.lyqs.data.remote.JmJson
import com.jmcomic_next.lyqs.data.remote.JmPaths
import com.jmcomic_next.lyqs.data.remote.JmRemote
import com.jmcomic_next.lyqs.data.remote.JmSession
import com.jmcomic_next.lyqs.data.auth.AuthStore
import com.jmcomic_next.lyqs.data.remote.dto.ActionResult
import com.jmcomic_next.lyqs.data.remote.dto.AlbumDetail
import com.jmcomic_next.lyqs.data.remote.dto.FavoriteListPayload
import com.jmcomic_next.lyqs.data.remote.dto.HistoryPayload
import com.jmcomic_next.lyqs.data.remote.dto.MemberInfo
import com.jmcomic_next.lyqs.data.remote.dto.CategoriesPayload
import com.jmcomic_next.lyqs.data.remote.dto.CategoryFilterPayload
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
/**
 * 搜索结果。
 *
 * 除了分页数据，还要带出 `redirect_aid` —— 按作品编号精确检索时服务端只回这一个字段，
 * 客户端应直接跳详情（源码 `Search.tsx` 的行为）。
 */
data class SearchResult(
    val page: PagedList = PagedList(),
    val redirectAid: String? = null,
)

class JmRepository(
    private val remote: JmRemote,
    private val authStore: AuthStore,
) {

    /** 账号会话状态，供界面读取登录态与会员信息。 */
    val auth: AuthStore get() = authStore

    private val session: JmSession get() = remote.session

    /**
     * 业务请求用的 OkHttpClient。
     *
     * 对外暴露是为了让 Coil 复用**同一个**客户端 —— 图片走的是独立通道，
     * 若各自建客户端，[com.jmcomic_next.lyqs.data.remote.AdBlockerInterceptor] 就只保护了一半流量。
     */
    val okHttp get() = remote.okHttp

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
        year: String? = null,
        month: String? = null,
    ): SearchResult {
        val payload = remote.get(
            JmPaths.SEARCH,
            SearchPayload.serializer(),
            buildMap {
                put("search_query", query)
                put("page", page.toString())
                order?.let { put("o", it) }
                type?.let { put("search_type", it) }
                year?.let { put("y", it) }
                month?.let { put("m", it) }
            },
        )
        // 搜索的 total 是字符串，而 latest 的是数字 —— 各按各的形态取，统一成 Int。
        // redirect_aid 非空表示「按作品编号精确命中」，应当直接打开详情而不是展示列表。
        return SearchResult(
            page = PagedList(payload.content, payload.total?.toIntOrNull() ?: 0),
            redirectAid = payload.redirectAid?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * 热门标签。
     *
     * 用于分类浏览页。之所以用它而不是 `categories` 接口：后者的条目带有
     * `slug` / `updated_at`，从渲染层看是**登录用户的收藏夹分类**，不适合做公开分类导航；
     * 而 `hot_tags` 是纯字符串数组，语义与数据形态都明确。
     */
    suspend fun hotTags(): List<String> =
        remote.get(JmPaths.HOT_TAGS, JsonElement.serializer()).let { el ->
            when (el) {
                is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf(String::isNotBlank) }
                is JsonObject -> el["list"]
                    ?.takeIf { it is JsonArray }
                    ?.let { list ->
                        (list as JsonArray).mapNotNull { (it as? JsonPrimitive)?.content }
                    }
                    .orEmpty()
                else -> emptyList()
            }
        }

    // ------------------------------------------------------------------
    // 账号
    // ------------------------------------------------------------------

    /**
     * 登录。
     *
     * 成功后立刻把凭证写进 [AuthStore] —— 这样后续请求（含界面刷新触发的那些）
     * 自然就带上 `Authorization`，不需要调用方再做一次「设置 token」的动作。
     */
    suspend fun login(username: String, password: String): MemberInfo {
        val info = remote.post(
            JmPaths.LOGIN,
            MemberInfo.serializer(),
            mapOf("username" to username, "password" to password),
        )
        val token = info.jwtToken?.takeIf { it.isNotBlank() }
            ?: throw JmException("登录成功但服务端未返回凭证", JmException.Kind.Parse)
        authStore.save(token, info)
        return info
    }

    /**
     * 注册。
     *
     * 注册接口**不返回 token**（官方注册完仍需登录），因此这里不写会话，
     * 只把服务端的结果返回给界面用于提示。
     */
    suspend fun register(
        username: String,
        password: String,
        passwordConfirm: String,
        email: String,
        gender: String,
    ): ActionResult = remote.post(
        JmPaths.REGISTER,
        ActionResult.serializer(),
        mapOf(
            "username" to username,
            "password" to password,
            "password_confirm" to passwordConfirm,
            "email" to email,
            "gender" to gender,
        ),
    )

    /** 忘记密码：按邮箱发送重置邮件。 */
    suspend fun forgotPassword(email: String): ActionResult = remote.post(
        JmPaths.FORGOT,
        ActionResult.serializer(),
        mapOf("email" to email),
    )

    /**
     * 登出。
     *
     * 通知服务端撤销凭证是「尽力而为」：即使这次请求失败（断网等），
     * 本地也必须登出 —— 否则界面显示已登录、实际凭证已被服务端撤销，
     * 会退化成每个请求都失败的状态。
     */
    suspend fun logout() {
        runCatching { remote.post(JmPaths.LOGOUT, ActionResult.serializer()) }
        authStore.clear()
    }

    // ------------------------------------------------------------------
    // 收藏 / 点赞 / 观看历史（都需要登录）
    // ------------------------------------------------------------------

    /**
     * 切换收藏。
     *
     * **同一个调用既收藏也取消** —— 服务端按当前状态自行判断，并在响应的 `type` 里
     * 告知实际动作（`add` / `remove` / `move` / `edit`）。因此客户端不需要先查状态再决定调什么，
     * 也就不会出现「本地以为已收藏、服务端其实没有」这类不一致。
     */
    suspend fun toggleFavorite(aid: String): ActionResult = remote.post(
        JmPaths.FAVORITE,
        ActionResult.serializer(),
        mapOf("aid" to aid),
    )

    /**
     * 收藏列表。
     *
     * @param folderId 收藏夹 id，留空为「全部」
     * @param order 排序，官方默认 `mr`
     */
    suspend fun favorites(
        page: Int = 1,
        folderId: String? = null,
        order: String = DEFAULT_FAVORITE_ORDER,
    ): FavoriteListPayload = remote.get(
        JmPaths.FAVORITE,
        FavoriteListPayload.serializer(),
        buildMap {
            put("page", page.toString())
            put("o", order)
            folderId?.takeIf { it.isNotBlank() }?.let { put("folder_id", it) }
        },
    )

    /**
     * 收藏夹编辑。
     *
     * @param type `add` 新建 / `edit` 改名 / `move` 归类 / `del` 删除
     */
    suspend fun editFavoriteFolder(
        type: String,
        folderId: String? = null,
        folderName: String? = null,
        aid: String? = null,
    ): ActionResult = remote.post(
        JmPaths.FAVORITE_FOLDER,
        ActionResult.serializer(),
        buildMap {
            put("type", type)
            folderId?.takeIf { it.isNotBlank() }?.let { put("folder_id", it) }
            folderName?.takeIf { it.isNotBlank() }?.let { put("folder_name", it) }
            aid?.takeIf { it.isNotBlank() }?.let { put("aid", it) }
        },
    )

    /** 观看历史。 */
    suspend fun history(page: Int = 1): HistoryPayload = remote.get(
        JmPaths.WATCH_LIST,
        HistoryPayload.serializer(),
        mapOf("page" to page.toString()),
    )

    /**
     * 删除一条观看历史。
     *
     * **这个 POST 不是「记录观看」，而是「删除历史条目」** —— 这一点极易搞反：
     * 官方代码里唯一的调用点是 `ComicList.tsx` 的 `handleDelWatchComic`，
     * 对应菜单项 `del_watch_history`。整个项目**没有任何地方用它上报观看**，
     * 观看记录是**服务端在读取章节时自动写入**的（请求带着已登录凭证）。
     *
     * 因此进入阅读页时**不要**调用它 —— 那等于每读一话就删掉一条历史。
     */
    suspend fun deleteHistory(comicId: String): ActionResult = remote.post(
        JmPaths.WATCH_LIST,
        ActionResult.serializer(),
        mapOf("id" to comicId),
    )

    /** 点赞。注意其响应的 `data` 里还有一层 `{code, status, msg}`，与封套的 code 是两个判断。 */
    suspend fun like(comicId: String): ActionResult = remote.post(
        JmPaths.LIKE,
        ActionResult.serializer(),
        mapOf("id" to comicId),
    )

    /** 分类树与标签组。 */
    suspend fun categories(): CategoriesPayload = remote.get(
        JmPaths.CATEGORIES,
        CategoriesPayload.serializer(),
    )

    /**
     * 按分类筛选作品。
     *
     * @param c 分类标识。父分类传 `slug`，子分类传 `"<父 slug>_<子 slug>"`。
     *   **为空时整个 `c` 参数会被省略** —— 实测发 `c=` 会让服务端返回
     *   `Could not connect to mysql!` 错误页（不是 JSON），而省略 `c` 是合法的
     *   「不筛选」语义（返回全站结果）。分类树里第一个「最新A漫」的 slug 正是空串。
     * @param order 排序键。分类筛选除搜索那几档外还多出月榜 `mv_m` 与周榜 `mp_w`
     *   （官方 `CatSortData`），搜索接口没有这两个。
     */
    suspend fun categoryFilter(
        c: String?,
        page: Int = 1,
        order: String? = null,
    ): PagedList {
        val payload = remote.get(
            JmPaths.CATEGORIES_FILTER,
            CategoryFilterPayload.serializer(),
            buildMap {
                c?.takeIf { it.isNotBlank() }?.let { put("c", it) }
                put("page", page.toString())
                order?.let { put("o", it) }
            },
        )
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
        /** 收藏列表的默认排序，官方 `defaultEditInitialState` 里是 `mr`。 */
        const val DEFAULT_FAVORITE_ORDER = "mr"

        /** 便捷构造，供 App 级容器使用。 */
        fun create(
            authStore: AuthStore,
            session: JmSession = JmSession(),
        ): JmRepository = JmRepository(JmRemote(session, authStore), authStore)
    }
}
