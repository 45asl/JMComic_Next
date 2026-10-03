package com.jmcomic_next.lyqs.data

import com.jmcomic_next.lyqs.data.remote.dto.DailyHistory
import com.jmcomic_next.lyqs.data.remote.dto.DailyHistoryOptions
import com.jmcomic_next.lyqs.data.remote.dto.NotificationUnread
import com.jmcomic_next.lyqs.data.remote.dto.NotificationPage
import com.jmcomic_next.lyqs.data.remote.dto.DailyCheckResult
import com.jmcomic_next.lyqs.data.remote.dto.DailyPayload
import com.jmcomic_next.lyqs.data.crypto.JmCrypto
import com.jmcomic_next.lyqs.data.prefs.BlockStore
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
import com.jmcomic_next.lyqs.data.remote.dto.ForumPayload
import com.jmcomic_next.lyqs.data.remote.dto.HistoryPayload
import com.jmcomic_next.lyqs.data.remote.dto.MemberInfo
import com.jmcomic_next.lyqs.data.remote.dto.CategoriesPayload
import com.jmcomic_next.lyqs.data.remote.dto.CreatorAuthor
import com.jmcomic_next.lyqs.data.remote.dto.CreatorEnvelope
import com.jmcomic_next.lyqs.data.remote.dto.CreatorPage
import com.jmcomic_next.lyqs.data.remote.dto.CreatorWork
import com.jmcomic_next.lyqs.data.remote.dto.CreatorWorkContent
import com.jmcomic_next.lyqs.data.remote.dto.CreatorWorkInfo
import com.jmcomic_next.lyqs.data.remote.dto.DownloadPayload
import com.jmcomic_next.lyqs.data.remote.dto.TagItem
import com.jmcomic_next.lyqs.data.remote.dto.TagPayload
import com.jmcomic_next.lyqs.data.remote.dto.WeekFilterPayload
import com.jmcomic_next.lyqs.data.remote.dto.WeekPayload
import com.jmcomic_next.lyqs.data.remote.dto.CategoryFilterPayload
import com.jmcomic_next.lyqs.data.remote.dto.JmSettings
import com.jmcomic_next.lyqs.data.remote.dto.ListItem
import com.jmcomic_next.lyqs.data.remote.dto.MoreListPayload
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
 * 创作者库的一页。
 *
 * 单独一个类型而不是复用 [PagedList]：后者的元素固定是 [ListItem]（漫画列表项），
 * 而这里放的是画师或作品，字段完全不同。硬塞进同一个类型只会让两边都不清楚。
 */
data class CreatorPageResult<T>(
    val items: List<T> = emptyList(),
    /** 总条数；服务端给的是字符串，取不到时为 0（未知）。 */
    val total: Int = 0,
)

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
    /** 屏蔽名单。由 App 容器注入，与账号存储同样是「本地状态」。 */
    val blockStore: BlockStore? = null,
) {

    /** 账号会话状态，供界面读取登录态与会员信息。 */
    val auth: AuthStore get() = authStore

    /** 当前屏蔽规则（无存储时视为空规则）。 */
    private fun rules(): BlockRules = blockStore?.snapshot() ?: BlockRules()

    /**
     * 按屏蔽规则过滤一页列表。
     *
     * **放在数据层而不是各页面**：列表出口有十来处（最新/搜索/分类/分区更多/周刊/追更/
     * 收藏/历史/随机推荐…），任何一处忘了过滤都会表现成「屏蔽没生效」，而这类漏网很难被发现。
     * 在这里统一收口，页面只需要显示 [PagedList.hidden]。
     */
    private fun PagedList.blockFiltered(): PagedList {
        val r = rules()
        if (r.isEmpty || items.isEmpty()) return this
        val kept = items.filterNot { r.hides(it) }
        return if (kept.size == items.size) this else copy(items = kept, hidden = items.size - kept.size)
    }

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
     * 三条重入规则，都是为了让一次失败不至于毁掉整个进程：
     *  - 还没主机 → 必须发现
     *  - 主机被标记为可疑（上一次请求是网络类失败）→ 重新发现并换一台，
     *    否则随机挑中的那个死域名会被一直用到进程结束
     *  - 图床主机还没拿到 → 重试 `setting`（它失败过一次就再没机会补齐，
     *    后果是所有封面与头像在整个会话里都加载不出来）
     *
     * @return 是否就绪。已在会话内成功引导过且无需重试时直接返回 true。
     * @throws JmException 主机发现全部失败时
     */
    suspend fun bootstrap(): Boolean = bootstrapLock.withLock {
        val ready = bootstrapped && session.isReady && !session.hostSuspect
        if (ready && session.imageHost != null) return@withLock true

        if (!ready) {
            val host = JmHostDiscovery.discover(session)
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
    suspend fun promote(): List<PromoteSection> {
        val sections = remote.get(JmPaths.PROMOTE, ListSerializer(PromoteSection.serializer()))
        val r = rules()
        if (r.isEmpty) return sections
        // 分区里的内容同样过滤；分区本身即使被清空也留着，避免首页区块顺序跳动
        return sections.map { section -> section.copy(content = section.content.filterNot { r.hides(it) }) }
    }

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
            ).blockFiltered()
            is JsonObject -> PagedList(
                items = el["list"]?.takeIf { it is JsonArray }?.let {
                    JmJson.decodeFromJsonElement(ListSerializer(ListItem.serializer()), it)
                } ?: emptyList(),
                total = el["total"].asIntOrZero(),
            ).blockFiltered()
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
            page = PagedList(payload.content, payload.total?.toIntOrNull() ?: 0).blockFiltered(),
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
        // 落盘要走 IO：凭证是经 Android Keystore 加密的，加解密 + 写 SharedPreferences
        // 放在主线程上做，是一次实实在在的卡顿（登录成功后界面正要切换）
        withContext(Dispatchers.IO) { authStore.save(token, info) }
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
        withContext(Dispatchers.IO) { authStore.clear() }
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
        JsonElement.serializer(),
        mapOf("aid" to aid),
    ).toActionResult()

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
    ).let { payload ->
        val r = rules()
        if (r.isEmpty) payload else payload.copy(list = payload.list.filterNot { r.hides(it) })
    }

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
    ).let { payload ->
        val r = rules()
        if (r.isEmpty) payload else payload.copy(list = payload.list.filterNot { r.hides(it) })
    }

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

    /**
     * 某作品的评论。
     *
     * @param mode 官方 `ForumTabItems` 里的取值（`all` 全部 / `manhua` 漫画评论 / `chat` 聊天室），
     *   详情页固定用 `all`（`Comment.tsx` 的 `loadList` 默认值）。
     */
    suspend fun comments(
        aid: String,
        page: Int = 1,
        mode: String = "all",
    ): ForumPayload = remote.get(
        JmPaths.FORUM,
        ForumPayload.serializer(),
        mapOf(
            "mode" to mode,
            "page" to page.toString(),
            "aid" to aid,
        ),
    )

    // ------------------------------------------------------------------
    // 期刊（周刊）/ 随机推荐 / 创作者库
    // ------------------------------------------------------------------

    /** 期刊列表：刊期（`categories`）与作品类型（`type`）。 */
    suspend fun weekIssues(): WeekPayload = remote.get(
        JmPaths.WEEK,
        WeekPayload.serializer(),
    )

    /**
     * 某期刊某类型下的作品。
     *
     * `id` 与 `type` 都必须取自 [weekIssues] 的返回 —— 实测刊期 id 是服务端的一串自增号
     * （而且与「第 N 期」并不相等），`type` 是 `manga` / `another` / `hanman` 三个字符串。
     *
     * @param page 1 起算（这个接口与 `promote_list` 不同）
     */
    suspend fun weekList(issueId: String, type: String, page: Int): PagedList {
        val payload = remote.get(
            JmPaths.WEEK_FILTER,
            WeekFilterPayload.serializer(),
            mapOf("id" to issueId, "type" to type, "page" to page.toString()),
        )
        return PagedList(payload.list, payload.total?.toIntOrNull() ?: 0).blockFiltered()
    }

    /** 随机推荐。`data` 是**裸数组**（与 `promote` 同形），不带分页信息。 */
    suspend fun randomRecommend(): List<ListItem> =
        remote.get(JmPaths.RANDOM_RECOMMEND_LIST, ListSerializer(ListItem.serializer()))
            .let { list -> rules().takeIf { !it.isEmpty }?.let { r -> list.filterNot { r.hides(it) } } ?: list }

    /** 画师列表。`search_query` 留空即不筛。 */
    suspend fun creatorAuthors(page: Int, query: String = ""): CreatorPageResult<CreatorAuthor> =
        remote.get(
            JmPaths.CREATOR_AUTHOR,
            CreatorEnvelope.serializer(CreatorPage.serializer(CreatorAuthor.serializer())),
            mapOf("page" to page.toString(), "search_query" to query),
        ).toResult()

    /**
     * 作品列表（按平台/语言筛）。
     *
     * @param searchValue 关键词
     * @param lang 语言，留空即不筛
     * @param source 来源平台（`patreon` / `fanbox` …），留空即不筛
     */
    suspend fun creatorWorks(
        page: Int,
        searchValue: String = "",
        lang: String = "",
        source: String = "",
    ): CreatorPageResult<CreatorWork> = remote.get(
        JmPaths.CREATOR_WORK,
        CreatorEnvelope.serializer(CreatorPage.serializer(CreatorWork.serializer())),
        mapOf(
            "page" to page.toString(),
            "search_value" to searchValue,
            "lang" to lang,
            "source" to source,
        ),
    ).toResult()

    /** 某画师名下的作品（`creator_work_detail`）。 */
    suspend fun creatorWorksByAuthor(
        id: String,
        lang: String = "",
        source: String = "",
    ): CreatorPageResult<CreatorWork> = remote.get(
        JmPaths.CREATOR_WORK_DETAIL,
        CreatorEnvelope.serializer(CreatorPage.serializer(CreatorWork.serializer())),
        mapOf("id" to id, "lang" to lang, "source" to source),
    ).toResult()

    /** 作品信息：作者、日期与一组相关作品。 */
    suspend fun creatorWorkInfo(id: String): CreatorWorkInfo = remote.get(
        JmPaths.CREATOR_WORK_INFO,
        CreatorWorkInfo.serializer(),
        mapOf("id" to id),
    )

    /**
     * 作品内容。
     *
     * 注意**并非每个作品都有内容**：实测有的作品回 `total_page: 0`、`images: []`，
     * 界面要能把这种当作「没有可看的内容」而不是错误。
     */
    suspend fun creatorWorkContent(id: String): CreatorWorkContent = remote.get(
        JmPaths.CREATOR_WORK_INFO_DETAIL,
        CreatorWorkContent.serializer(),
        mapOf("id" to id),
    )

    // ------------------------------------------------------------------
    // 需要登录的漫画侧功能：追更 / 标签收藏 / 下载 / 发评论
    // ------------------------------------------------------------------

    /**
     * 查询是否已追更。
     *
     * 这个接口的 `data` 形态**没有文档且随版本变动**，实测未登录时是
     * `{"status":"fail","msg":"請先登入會員"}`，登录后可能是布尔或对象，
     * 因此这里宽容地判真：只有明确表示「真」才算追更，其余一律按未追更处理 ——
     * 反过来（把失败当已追更）会让用户以为自己关注过了。
     */
    suspend fun isTracked(aid: String): Boolean =
        remote.getRaw(JmPaths.SERTRACKING, mapOf("id" to aid)).trackedOrFalse()

    /** 追更开关。**同一个 POST 既是追更也是取关**，响应里带一句结果文案。 */
    suspend fun toggleTracking(aid: String): ActionResult = remote.post(
        JmPaths.SERTRACKING,
        JsonElement.serializer(),
        mapOf("id" to aid),
    ).toActionResult()

    /** 追更列表（上限 500）。注意这个接口是 **POST**。 */
    suspend fun trackingList(page: Int = 1): PagedList {
        val payload = remote.post(
            JmPaths.TRACKING_LIST,
            MoreListPayload.serializer(),
            mapOf("page" to page.toString()),
        )
        // 这个接口的键是 `item` / `totalCnt`（见 MoreListPayload 的说明）
        return PagedList(payload.items, payload.totalEither).blockFiltered()
    }

    /** 收藏的标签（上限 50）。 */
    suspend fun favoriteTags(): List<TagItem> =
        remote.get(JmPaths.TAGS_FAVORITE, TagPayload.serializer()).list

    /** 收藏标签的增删。`type` 取 `add` / `remove`，`tags` 在请求里是**逗号分隔**的字符串。 */
    suspend fun updateFavoriteTags(type: String, tags: List<String>): ActionResult = remote.post(
        JmPaths.TAGS_FAVORITE_UPDATE,
        JsonElement.serializer(),
        mapOf("type" to type, "tags" to tags.joinToString(",")),
    ).toActionResult()

    /**
     * 整部作品的下载信息。
     *
     * **需要登录，而且失败不是 401**：实测未登录时是 HTTP 200 +
     * `{"status":"0","msg":"請先登入"}`，所以判断必须落在 [DownloadPayload.status] 上，
     * 不能只看 HTTP 状态码。
     */
    suspend fun albumDownload(aid: String): DownloadPayload = remote.get(
        "${JmPaths.ALBUM_DOWNLOAD}/$aid",
        DownloadPayload.serializer(),
    )

    /** 发表评论。`commentId` 非空时是对某条评论的回复。 */
    suspend fun sendComment(aid: String, comment: String, commentId: String? = null): ActionResult =
        remote.post(
            JmPaths.COMMENT_SEND,
            JsonElement.serializer(),
            buildMap {
                put("comment", comment)
                put("aid", aid)
                commentId?.takeIf { it.isNotBlank() }?.let { put("comment_id", it) }
            },
        ).toActionResult()

    /** 删除自己发的评论。 */
    suspend fun deleteComment(commentId: String, aid: String? = null): ActionResult = remote.post(
        JmPaths.COMMENT_DELETE,
        JsonElement.serializer(),
        buildMap {
            put("comment_id", commentId)
            aid?.takeIf { it.isNotBlank() }?.let { put("aid", it) }
        },
    ).toActionResult()

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
        return PagedList(payload.content, payload.total?.toIntOrNull() ?: 0).blockFiltered()
    }

    /**
     * 首页某个推荐分区的完整列表（「更多」）。
     *
     * @param page **0 起算**（与 `latest` 一致，服务端约定），官网源码里
     *   `Comic.tsx` 也明确写着「page 是 0-indexed（第一頁是 0）」。
     */
    suspend fun promoteList(id: String, page: Int): PagedList = remote.get(
        JmPaths.PROMOTE_LIST,
        MoreListPayload.serializer(),
        mapOf("id" to id, "page" to page.toString()),
    ).let { PagedList(it.items, it.totalEither).blockFiltered() }

    /**
     * 连载更新表（每周更新）。
     *
     * @param type `all` 全部 / `manga` 漫画 / `hanman` 韩漫
     * @param date **0 完结，1..7 周一..周日**（官方 `getWeekInfo`：把 JS 的
     *   `getDay()` 从「周日=0」换算成「周一=1」，第 8 个标签是「完结」= 0）
     * @param page **1 起算**，与其它列表接口相反
     *
     * 返回的 [PagedList.total] 恒为 0 —— 这个接口不给总数（见 [JmPaths.SERIALIZATION]），
     * 调用方只能靠「本页是否为空」判断到底。
     */
    suspend fun weeklyUpdate(
        type: String = WEEKLY_TYPE_ALL,
        date: Int,
        page: Int,
    ): PagedList = remote.get(
        JmPaths.SERIALIZATION,
        MoreListPayload.serializer(),
        mapOf(
            "type" to type,
            "date" to date.toString(),
            "page" to page.toString(),
        ),
    ).let { PagedList(it.list, total = 0) }

    /**
     * 当前签到活动（1.5.4）。需要登录：`user_id` 就是账号 uid。
     *
     * 没有活动时服务端也会回 200，只是 `daily_id` 为空 —— 调用方要判空，
     * 不能假定"拿到 daily 就一定能打卡"。
     */
    suspend fun daily(uid: String): DailyPayload = remote.get(
        JmPaths.DAILY,
        DailyPayload.serializer(),
        mapOf("user_id" to uid),
    )

    /** 签到历史可选的年份（1.5.4 历史日历）。 */
    suspend fun dailyHistoryOptions(uid: String): DailyHistoryOptions = remote.get(
        JmPaths.DAILY_LIST,
        DailyHistoryOptions.serializer(),
        mapOf("user_id" to uid),
    )

    /** 某一年（[data] 就是 `dailyHistoryOptions` 里的 title）的签到记录。 */
    suspend fun dailyHistory(data: String): DailyHistory = remote.post(
        JmPaths.DAILY_LIST_FILTER,
        DailyHistory.serializer(),
        mapOf("data" to data),
    )

    /** 打卡。重复打卡由服务端在 `msg` 里说明，不当异常处理（见 [Daily.isAlreadyChecked]）。 */
    suspend fun dailyCheck(uid: String, dailyId: String): DailyCheckResult = remote.post(
        JmPaths.DAILY_CHECK,
        DailyCheckResult.serializer(),
        mapOf("user_id" to uid, "daily_id" to dailyId),
    )

    /**
     * 通知列表（1.5.3）。`type` 取 `all` / `comic_follow` / `site_notice`。
     *
     * 追更通知由**服务端**在作品更新时生成 —— 客户端不需要（也不应该）自己
     * 拿阅读时间与 `update_at` 去猜有没有更新。
     */
    suspend fun notifications(
        type: String = "all",
        page: Int = 1,
    ): NotificationPage = NotificationPage.from(
        // 先取 JsonElement 再解释：这个接口的 data 有时是裸数组、有时是 {list,total}，
        // 直接按对象反序列化会在裸数组上解析失败（官方源码两种都兜）
        remote.get(
            JmPaths.NOTIFICATIONS,
            JsonElement.serializer(),
            mapOf("type" to type, "page" to page.toString()),
        ),
    )

    /** 未读通知数量。拉它就能做角标，不需要任何后台任务。 */
    suspend fun notificationsUnread(): NotificationUnread = NotificationUnread.from(
        remote.get(JmPaths.NOTIFICATIONS_UNREAD, JsonElement.serializer()),
    )

    /** 标记通知已读/未读（POST `{id, read}`）。 */
    suspend fun markNotificationRead(id: String, read: Boolean): ActionResult = remote.post(
        JmPaths.NOTIFICATIONS,
        ActionResult.serializer(),
        mapOf("id" to id, "read" to if (read) "1" else "0"),
    )

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
     * 画师头像。
     *
     * 服务端只给文件名（实测 `author_avatar` 形如 `/media/library/artists/7118/icon/18446886.gif`，
     * 有时只给最后一段），两种都要接：已经是完整路径的直接用，否则按模板拼。
     */
    fun artistIconUrl(author: CreatorAuthor): String? = creatorImageUrl(
        id = author.id,
        template = JmPaths.ARTIST_ICON_TEMPLATE,
        path = author.avatar,
    )

    /** 画师横幅，规则同 [artistIconUrl]。 */
    fun artistBannerUrl(author: CreatorAuthor): String? = creatorImageUrl(
        id = author.id,
        template = JmPaths.ARTIST_BANNER_TEMPLATE,
        path = author.background,
    )

    /**
     * 作品封面。
     *
     * 实测 `work_image` 给的是完整相对路径（`/media/library/album/1100557/thumb/album.jpg`），
     * 因此直接拼图床主机即可，不需要模板。
     */
    fun creatorWorkCoverUrl(work: CreatorWork): String? =
        work.image?.takeIf { it.isNotBlank() }?.let { session.imageUrl(it) }

    /** 作品内容里的图片（同样是相对路径）。 */
    fun creatorContentUrl(image: String): String = session.imageUrl(image)

    private fun creatorImageUrl(id: String, template: String, path: String?): String? {
        val raw = path?.takeIf { it.isNotBlank() } ?: return null
        if (raw.startsWith("http")) return raw
        // 已经是带目录的路径就直接用；只有文件名时才套模板
        val full = if (raw.contains('/')) raw else template.format(id, raw)
        return session.imageUrl(full)
    }

    /** 用户头像地址。`photo` 是文件名，需按 `media/users/<photo>` 拼图床主机。 */
    fun avatarUrl(photo: String?): String? = photo
        ?.takeIf { it.isNotBlank() }
        ?.let { session.imageUrl(JmPaths.AVATAR_TEMPLATE.format(it)) }

    /**
     * 判断某张图片是否需要做切片还原。
     *
     * GIF 不切；`aid` 小于 `scramble_id` 的老漫画也不切 —— 两条规则都来自
     * 源码 `scramble_image` 的入口判断。
     */
    fun needsUnscramble(imageUrl: String, aid: Int, scrambleId: Int): Boolean =
        JmCrypto.needsUnscramble(imageUrl, aid, scrambleId)

    /** 把创作者库那层 `{status, data:{total, content}}` 拉平成结果类型。 */
    private fun <T> CreatorEnvelope<CreatorPage<T>>.toResult(): CreatorPageResult<T> =
        CreatorPageResult(
            items = data?.content.orEmpty(),
            total = data?.total?.toIntOrNull() ?: 0,
        )


    /** 宽容地把 `total` 读成 Int：服务端有时给字符串、有时给数字、有时干脆不给。 */
    private fun JsonElement?.asIntOrZero(): Int =
        this?.let { runCatching { it.jsonPrimitive.content.toInt() }.getOrNull() } ?: 0

    companion object {
        /** 收藏列表的默认排序，官方 `defaultEditInitialState` 里是 `mr`。 */
        const val DEFAULT_FAVORITE_ORDER = "mr"

        /**
         * 首页推荐分区里「连载更新」那一块的固定 id。
         *
         * 官方 `Comic.tsx` 用 `queryId === "26"` 判定「这个分区要按每周更新表来渲染」
         * （实测该分区标题是「连载更新→右滑看更多→」）。这个 id 是服务端约定，
         * 推导不出来，因此显式命名。
         */
        const val WEEKLY_SECTION_ID = "26"

        /** 连载更新表 `type` 参数的三个取值（官方 `ComicType`）。 */
        const val WEEKLY_TYPE_ALL = "all"
        const val WEEKLY_TYPE_MANGA = "manga"
        const val WEEKLY_TYPE_HANMAN = "hanman"

        /** 便捷构造，供 App 级容器使用。 */
        fun create(
            authStore: AuthStore,
            session: JmSession = JmSession(),
            blockStore: BlockStore? = null,
        ): JmRepository = JmRepository(JmRemote(session, authStore), authStore, blockStore)
    }
}

/**
 * 把动作类接口的 `data` 归一成 ActionResult。
 *
 * 这个服务端的动作响应**有两种形态**，实测都出现过：
 *  - 对象：`{status:"ok", type:"add", msg:"..."}`
 *  - **一整句话**：`"已追踪!"` / `"已取消追踪!"`
 *
 * 只按对象解析的话，第二种会抛解析错 —— 用户刚做的操作其实成功了，
 * 界面却报「响应解析失败」，而且（在重试逻辑下）请求还会被重发一次。
 * 纯文本一律当作「成功 + 这句话就是提示」，因为它本来就是给用户看的内容。
 */
internal fun JsonElement?.toActionResult(): ActionResult = when (this) {
    null -> ActionResult()
    is JsonObject -> runCatching {
        JmJson.decodeFromJsonElement(ActionResult.serializer(), this)
    }.getOrElse { ActionResult() }

    is JsonPrimitive -> ActionResult(status = "ok", msg = content)
    else -> ActionResult()
}

/**
 * 判断追更状态。
 *
 * 输入是接口返回的**原文**（可能是一句人话，如「已追踪!」），因此按关键字判读：
 * 含「取消」为假，含「追踪/追更」为真，`true`/`1` 为真。
 * 把失败响应当成「已追更」比反过来危险 —— 用户会以为自己早就关注了，于是再也不会去点。
 */
internal fun String.trackedOrFalse(): Boolean {
    val text = trim().trim('"')
    return when {
        text.isEmpty() -> false
        text.contains("取消") -> false
        text.equals("true", ignoreCase = true) || text == "1" -> true
        text.equals("false", ignoreCase = true) || text == "0" -> false
        // 实测未追更/已追更都可能回一句中文文案，含「追踪/追更」即视为已追更
        text.contains("追踪") || text.contains("追更") -> true
        text.contains("\"track\":true") || text.contains("\"is_track\":true") -> true
        else -> false
    }
}
