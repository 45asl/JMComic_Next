package com.jmcomic_next.lyqs.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 字段结构以还原源码中**手写的** `InterFace.ts` 为准 ——
 * 那是这套 API 唯一一份非反编译、带真实类型声明的资料，
 * 比从 JSX 访问点倒推可靠得多。
 */

/** 分类引用。列表项上的 `category` / `category_sub` 都是这个结构。 */
@Serializable
data class CategoryRef(
    @Serializable(with = FlexStringOrNull::class) val id: String? = null,
    val title: String? = null,
)

/**
 * 漫画列表项（`InterFace.ts` 中 `LatestResponse` / `SearchResponse` /
 * `MoreListResponse` 的数组元素）。
 *
 * 注意 `update_at` 在类型定义里是 **number**（时间戳），而它会被拼进封面 URL 当版本号，
 * 因此这里统一按字符串存。
 */
@Serializable
data class ListItem(
    @Serializable(with = FlexString::class) val id: String = "",
    val name: String? = null,
    val author: String? = null,
    val description: String? = null,
    /** 服务端下发的封面地址。可能与客户端按 id 拼出的模板不同，优先用它。 */
    val image: String? = null,
    val category: CategoryRef? = null,
    @SerialName("category_sub") val categorySub: CategoryRef? = null,
    @SerialName("update_at")
    @Serializable(with = FlexStringOrNull::class) val updateAt: String? = null,
    @SerialName("is_favorite")
    @Serializable(with = FlexBool::class) val isFavorite: Boolean = false,
    @Serializable(with = FlexBool::class) val liked: Boolean = false,
    /**
     * 收录日期。只在搜索结果的「最旧」排序里用到 —— 那一档官方客户端**在本地**按它重排，
     * 而不是完全交给服务端（见 `Search.tsx` 的 `isLocalOldest`）。
     */
    @SerialName("adddate")
    @Serializable(with = FlexStringOrNull::class) val addDate: String? = null,
)

/**
 * 首页推荐分区（`InterFace.ts` 的 `PromoteResponse.data` 元素）。
 *
 * 首页不是一长条列表，而是**若干带标题的区块**，每个区块自带一组漫画。
 * `filter_val` / `slug` / `type` 用于「查看更多」时请求对应分区，
 * `filter_val` 在上层没有被读取过，属于服务端预留。
 */
@Serializable
data class PromoteSection(
    @Serializable(with = FlexString::class) val id: String = "",
    val title: String? = null,
    val slug: String? = null,
    val type: String? = null,
    @SerialName("filter_val") val filterVal: String? = null,
    val content: List<ListItem> = emptyList(),
)

/** 搜索响应（`InterFace.ts` 的 `SearchResponse.data`）。数组键是 `content`。 */
@Serializable
data class SearchPayload(
    @SerialName("search_query") val searchQuery: String? = null,
    @Serializable(with = FlexStringOrNull::class) val total: String? = null,
    val content: List<ListItem> = emptyList(),
    /**
     * 命中「按作品编号精确检索」时服务端会回这个字段，客户端应**直接跳到详情**而不是展示列表。
     * 依据 `Search.tsx`：`if (redirect_aid) navigate('/comic/detail?id=' + redirect_aid)` 并中止。
     */
    @SerialName("redirect_aid")
    @Serializable(with = FlexStringOrNull::class) val redirectAid: String? = null,
)

/**
 * 「查看更多」响应（`InterFace.ts` 的 `MoreListResponse.data`）。
 *
 * 数组键**不统一**，实测两种都出现过：
 *  - `promote_list` / `week/filter`：`{total, list}`
 *  - `album_tracking`（追更列表）：**`{item, totalCnt}`** —— 键名完全是另一套
 *
 * 只认 `list` 的话，追更列表会静默显示成 0 条（用过一次账号才发现）。
 * 因此两个键都接，取非空的那个；总数同理。
 */
@Serializable
data class MoreListPayload(
    @Serializable(with = FlexStringOrNull::class) val total: String? = null,
    val list: List<ListItem> = emptyList(),
    /** 追更列表用的键。 */
    val item: List<ListItem> = emptyList(),
    @SerialName("totalCnt")
    @Serializable(with = FlexStringOrNull::class) val totalCount: String? = null,
) {
    /** 实际条目：优先 `list`，它为空时用 `item`。 */
    val items: List<ListItem> get() = list.ifEmpty { item }

    /** 总条数，两个键名都看。 */
    val totalEither: Int get() = total?.toIntOrNull() ?: totalCount?.toIntOrNull() ?: 0
}

/**
 * 漫画详情（`album` 接口）。
 *
 * `series` 就是章节目录 —— 源码里 `chapter` 接口虽有定义但**全项目零调用**
 * （连打包产物里都没有引用），章节直接内嵌在详情响应里，因此不单独建模该接口。
 */
@Serializable
data class AlbumDetail(
    @Serializable(with = FlexString::class) val id: String = "",
    val name: String? = null,
    /**
     * 作者是**数组**而不是字符串 —— 依据 `Desc.tsx` 的
     * `detailList.author?.map((auther: string, i) => ...)`，且每个作者都可点击跳标签搜索。
     * 用 [FlexStringList] 以同时容忍服务端回 `"a,b"` 这种逗号串的历史形态。
     */
    @Serializable(with = FlexStringList::class) val author: List<String> = emptyList(),
    @Serializable(with = FlexStringList::class) val actors: List<String> = emptyList(),
    @Serializable(with = FlexStringList::class) val tags: List<String> = emptyList(),
    @Serializable(with = FlexStringList::class) val works: List<String> = emptyList(),
    @SerialName("addtime")
    @Serializable(with = FlexStringOrNull::class) val addTime: String? = null,
    val description: String? = null,
    @SerialName("comment_total")
    @Serializable(with = FlexInt::class) val commentTotal: Int = 0,
    @SerialName("is_favorite")
    @Serializable(with = FlexBool::class) val isFavorite: Boolean = false,
    @Serializable(with = FlexBool::class) val liked: Boolean = false,
    @Serializable(with = FlexInt::class) val likes: Int = 0,
    @SerialName("total_photos")
    @Serializable(with = FlexInt::class) val totalPhotos: Int = 0,
    @SerialName("total_views")
    @Serializable(with = FlexStringOrNull::class) val totalViews: String? = null,
    @SerialName("series_id")
    @Serializable(with = FlexStringOrNull::class) val seriesId: String? = null,
    val series: List<SeriesItem> = emptyList(),
    @SerialName("related_list") val relatedList: List<ListItem> = emptyList(),
    @SerialName("real_link") val realLink: String? = null,
)

/**
 * 章节。
 *
 * 源码中只用两个字段：`id`（与漫画 id 同为字符串，用于跳转阅读）
 * 与 `sort`（章节序号）。界面每 10 章一页（`Detail.tsx` 的 `chunkSize = 10`）。
 */
@Serializable
data class SeriesItem(
    @Serializable(with = FlexString::class) val id: String = "",
    @Serializable(with = FlexStringOrNull::class) val sort: String? = null,
    val name: String? = null,
)

/** 阅读数据（`comic_read` 接口）。 */
@Serializable
data class ReadPayload(
    /** 这里的 `id` 就是反切片算法里的 `aid`。 */
    @Serializable(with = FlexInt::class) val id: Int = 0,
    val name: String? = null,
    /** 低于该值的漫画不做切片还原。 */
    @SerialName("scramble_id")
    @Serializable(with = FlexInt::class) val scrambleId: Int = 0,
    val images: List<ReadImage> = emptyList(),
    @SerialName("total_page")
    @Serializable(with = FlexInt::class) val totalPage: Int = 0,
)

/** 单页图片。 */
@Serializable
data class ReadImage(
    /** 完整图片 URL。 */
    val image: String = "",
    /** 仅用于 DOM id（`img_<page>`），**不参与**反切片计算。 */
    @Serializable(with = FlexInt::class) val page: Int = 0,
) {
    /**
     * 反切片算法里作为 `page` 参数参与 md5 的那个字符串。
     *
     * **不是 [page]**，而是图片文件名去掉扩展名后的主体。
     * 依据是 `Read.tsx` 的 `alt={d.image?.match(/\/([^\/]+)\.webp/)?.[1]}`，
     * 随后该 `alt` 被当作 `page` 传进 `scramble_image`。
     * 用错这一个参数会让整页图片还原成错序，所以单独抽成具名属性。
     */
    val fileNameStem: String
        get() = image.substringAfterLast('/').substringBeforeLast('.')
}

/**
 * 分类树（`categories` 接口）。
 *
 * 两个互补的部分：
 *  - [categories] 是层级导航，父分类下挂子分类
 *  - [blocks] 是若干组标签（`content` 为纯字符串），用于「按标签浏览」
 *
 * 筛选时传给 `categories/filter` 的 `c` 参数取 `slug`，
 * 若选了子分类则取 `"<父 slug>_<子 slug>"`（官方 `Header.tsx` 的拼法）。
 */
@Serializable
data class CategoriesPayload(
    val categories: List<CategoryNode> = emptyList(),
    val blocks: List<CategoryBlock> = emptyList(),
)

/** 分类节点。 */
@Serializable
data class CategoryNode(
    @Serializable(with = FlexString::class) val slug: String = "",
    val name: String? = null,
    @SerialName("sub_categories") val subCategories: List<SubCategory> = emptyList(),
)

/** 子分类。 */
@Serializable
data class SubCategory(
    @Serializable(with = FlexString::class) val slug: String = "",
    val name: String? = null,
)

/** 一组标签。 */
@Serializable
data class CategoryBlock(
    val title: String? = null,
    @Serializable(with = FlexStringList::class) val content: List<String> = emptyList(),
)

/** 分类筛选结果（`categories/filter` 接口）。数组键是 `content`，与搜索一致。 */
@Serializable
data class CategoryFilterPayload(
    val content: List<ListItem> = emptyList(),
    @Serializable(with = FlexStringList::class) val tags: List<String> = emptyList(),
    @Serializable(with = FlexStringOrNull::class) val total: String? = null,
)

/** 应用配置（`setting` 接口，`InterFace.ts` 的 `SettingData`）。只保留客户端会用到的字段。 */
@Serializable
data class JmSettings(
    /** 图床主机，封面与头像都挂在它下面。 */
    @SerialName("img_host") val imgHost: String? = null,
    /** 站点主机，用于分享链接。 */
    @SerialName("main_web_host") val mainWebHost: String? = null,
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("is_cn")
    @Serializable(with = FlexBool::class) val isCn: Boolean = false,
    val version: String? = null,
    /**
     * 图源/线路列表。`key == 0` 是官方客户端里的「快速线路」，
     * 选中时 `comic_read` 会多带一个 `express=on`（见 [com.jmcomic_next.lyqs.data.JmRepository.read]）。
     */
    @SerialName("app_shunts") val appShunts: List<AppShunt> = emptyList(),

    /**
     * 服务端当前对应的**官方客户端**版本。
     *
     * 注意它指的是官方 App 的版本，不是本应用的版本 —— 因此不能拿它做「有新版本」的提示。
     * 它的实际用处是**协议对齐的探针**：本应用在 `Tokenparam` 里上报的版本就是照官方版本填的，
     * 一旦这里变化，说明服务端面向了新的客户端行为，当前实现可能需要跟进。
     */
    @SerialName("jm3_version") val jm3Version: String? = null,
    /** 版本公告正文（官方按 `\n` 逐行渲染）。 */
    @SerialName("jm3_version_info") val jm3VersionInfo: String? = null,
    /** 官方客户端的下载地址。 */
    @SerialName("jm3_download_url") val jm3DownloadUrl: String? = null,
    /** 站点落地页。 */
    @SerialName("app_landing_page") val appLandingPage: String? = null,
)

/** 图源/线路条目，`InterFace.ts` 的 `SettingData.app_shunts` 元素。 */
@Serializable
data class AppShunt(
    @Serializable(with = FlexInt::class) val key: Int = 0,
    val title: String? = null,
)

/**
 * 归一分页结果。
 *
 * 三种服务端形态都要接受，由 `JmRepository` 负责归一：
 *  - `latest`：裸数组，或 `{list,total}`
 *  - `search`：`{search_query,total,content}`
 *  - 更多列表：`{total,list}`
 *
 * `total == 0` 表示服务端未提供总数（此时列表可无限下滑）。
 */
data class PagedList(
    val items: List<ListItem> = emptyList(),
    val total: Int = 0,
) {
    val hasTotal: Boolean get() = total > 0
}

/**
 * 评论作者的经验信息。只用到等级 —— 官方在昵称旁展示一个等级徽章。
 */
@Serializable
data class ExpInfo(
    @Serializable(with = FlexInt::class) val level: Int = 0,
)

/**
 * 一条评论（`forum` 接口）。
 *
 * 字段名是**大写短名**（`CID`/`UID`/`AID`/`NID`），而正文与作者信息是小写 ——
 * 依据 `ForumList.tsx` 的实际访问点：`d.CID`、`d.UID`、`item.content`、
 * `d.nickname`、`d.photo`、`d.expinfo.level`。
 * 写错大小写不会报错，只会静默取到空值，因此这里逐字段对照过。
 */
@Serializable
data class CommentItem(
    @SerialName("CID")
    @Serializable(with = FlexString::class) val commentId: String = "",
    @SerialName("UID")
    @Serializable(with = FlexStringOrNull::class) val uid: String? = null,
    @SerialName("AID")
    @Serializable(with = FlexStringOrNull::class) val aid: String? = null,
    @SerialName("BID")
    @Serializable(with = FlexStringOrNull::class) val bid: String? = null,
    @SerialName("NID")
    @Serializable(with = FlexStringOrNull::class) val nid: String? = null,
    /** 正文。 */
    val content: String? = null,
    /** 发表时间。服务端以字符串下发。 */
    @Serializable(with = FlexStringOrNull::class) val addtime: String? = null,
    /** 作者昵称。 */
    val nickname: String? = null,
    /** 作者头像文件名，需拼图床主机（`media/users/<photo>`）。 */
    val photo: String? = null,
    val expinfo: ExpInfo? = null,
    /** 主题标题，评论列表里通常为空。 */
    val name: String? = null,
    /** 楼中楼回复。只展示数量，不展开（官方也只在详情里展开一层）。 */
    @SerialName("replys") val replies: List<CommentItem> = emptyList(),
) {
    /** 展示用作者名，缺失时回退为匿名。 */
    val authorName: String get() = nickname?.takeIf { it.isNotBlank() } ?: "匿名"
}

/** 评论列表响应（`forum` 接口的 `data`）。 */
@Serializable
data class ForumPayload(
    val list: List<CommentItem> = emptyList(),
    @Serializable(with = FlexStringOrNull::class) val total: String? = null,
) {
    /**
     * 总条数，未知时为 0。
     *
     * 不用 `list.size` 兜底：分页判断是 `已加载数 >= total`，
     * 而「本页条数 == 总数」在第一页永远成立 —— 那会让评论**永远停在第一页**。
     */
    val totalCount: Int get() = total?.toIntOrNull() ?: 0
}

// ---------------------------------------------------------------------------
// 期刊（周刊）、随机推荐、创作者库
//
// 这三块的 data 形态互不相同，而且与前面的列表都不一样 ——
// 全部按实测响应建模，注释里写了实测样例。
// ---------------------------------------------------------------------------

/**
 * 期刊列表（`week` 接口的 `data`）。
 *
 * 实测：
 * ```
 * {"categories":[{"id":"259","title":"","time":"2026第258期09.25 - 09.18"},...],
 *  "type":[{"id":"manga","name":"日漫"},...]}
 * ```
 *
 * 两点要注意：`title` 实测是**空串**，能显示的是 `time`；
 * 刊期 id 与「第 N 期」并不相等（id 259 对应第 258 期），所以界面上不能拿 id 当期号显示。
 */
@Serializable
data class WeekPayload(
    val categories: List<WeekCategory> = emptyList(),
    val type: List<WeekType> = emptyList(),
)

/** 一个刊期。 */
@Serializable
data class WeekCategory(
    @Serializable(with = FlexString::class) val id: String = "",
    val title: String? = null,
    /** 期号与日期范围，例如「2026第258期09.25 - 09.18」。 */
    val time: String? = null,
) {
    /** 展示用名称：`title` 实测为空，回退到 [time]。 */
    val label: String get() = title?.takeIf { it.isNotBlank() } ?: time.orEmpty()
}

/**
 * 作品类型（`hanman` 韩漫 / `another` 其他 / `manga` 日漫）。
 *
 * 展示名的键名是 **`title`**，不是 `name` —— 实测原文：
 * `"type":[{"id":"hanman","title":"韩漫"},{"id":"another","title":"其他"},{"id":"manga","title":"日漫"}]`。
 * 按 `name` 读会全部拿到 null，界面上就只剩 `hanman` 这种原始 id
 * （这个 bug 是在真机上看界面时发现的：单元测试按同一个错猜测写的，所以没拦住）。
 */
@Serializable
data class WeekType(
    @Serializable(with = FlexString::class) val id: String = "",
    val title: String? = null,
    /** 兜底：服务端别处用过 `name`，留着不吃亏。 */
    val name: String? = null,
) {
    /** 展示名，缺失时回退到 id。 */
    val label: String
        get() = title?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: id
}

/** 期刊内的作品列表（`week/filter` 的 `data`）：`{total, list}`，与「更多列表」同形。 */
@Serializable
data class WeekFilterPayload(
    @Serializable(with = FlexStringOrNull::class) val total: String? = null,
    val list: List<ListItem> = emptyList(),
)

/**
 * 创作者库的通用外壳。
 *
 * `creator_author` 与 `creator_work` 都是**双层封套**：
 * 外层是统一的 `{code, data}`，解出来的 `data` 里还有一层 `{status, data:{total, content}}`，
 * 而且 `status` 一个回字符串 `"200"`、一个回数字 `200`（实测如此）。因此 status 用宽容类型接，
 * 并且不拿它做判断 —— 真正的成败看外层封包的 code。
 */
@Serializable
data class CreatorEnvelope<T>(
    @Serializable(with = FlexStringOrNull::class) val status: String? = null,
    val data: T? = null,
)

/** 分页内容（创作者库那层 `data`）。 */
@Serializable
data class CreatorPage<T>(
    @Serializable(with = FlexStringOrNull::class) val total: String? = null,
    val content: List<T> = emptyList(),
)

/** 画师（`creator_author` 的条目）。 */
@Serializable
data class CreatorAuthor(
    @Serializable(with = FlexString::class) val id: String = "",
    @SerialName("author_name") val name: String? = null,
    /** 相对时间文案，服务端直接给「183 天 前」这种字符串。 */
    @SerialName("update_date") val updateDate: String? = null,
    /** 头像与横幅只给文件名，要按 `media/library/artists/<id>/{icon,banner}/<file>` 拼图床。 */
    @SerialName("author_avatar") val avatar: String? = null,
    @SerialName("background_image") val background: String? = null,
)

/** 作品（`creator_work` 的条目，也用于作品信息里的相关作品）。 */
@Serializable
data class CreatorWork(
    @Serializable(with = FlexString::class) val id: String = "",
    @SerialName("work_title") val title: String? = null,
    @SerialName("work_image") val image: String? = null,
    @SerialName("work_date") val date: String? = null,
    /** 来源平台，例如 `patreon` / `fanbox`。 */
    @SerialName("platform_name") val platform: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("author_id") val authorId: String? = null,
)

/** 作品信息（`creator_work_info` 的 `data`）。 */
@Serializable
data class CreatorWorkInfo(
    @SerialName("work_title") val title: String? = null,
    @SerialName("work_date") val date: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("related_works") val relatedWorks: List<CreatorWork> = emptyList(),
)

/**
 * 作品内容（`creator_work_info_detail` 的 `data`）。
 *
 * 形态与阅读数据接近（`images` + 正文），实测 `total_page` 可能为 0、`images` 为空 ——
 * 即**并非每个作品都有可看的内容**，界面要能接住这种情况。
 */
@Serializable
data class CreatorWorkContent(
    @Serializable(with = FlexString::class) val id: String = "",
    val name: String? = null,
    @SerialName("total_page")
    @Serializable(with = FlexInt::class) val totalPage: Int = 0,
    val images: List<CreatorImage> = emptyList(),
    val content: String? = null,
    @SerialName("adddt") val addDate: String? = null,
)

/** 作品内容里的一张图。 */
@Serializable
data class CreatorImage(
    /** 相对路径，需拼图床主机。 */
    val image: String = "",
)

/** 收藏标签列表（`tags_favorite`）。 */
@Serializable
data class TagPayload(
    val list: List<TagItem> = emptyList(),
)

/**
 * 一个被收藏的标签。
 *
 * 字段名是 **`tag`** 而不是 `name`/`id`（依据 `TagMarkList.tsx` 里 `item.tag`；
 * 选中判断、删除、跳搜索全用它）。写错不会报错，只会静默变成一串空标签。
 */
@Serializable
data class TagItem(
    val tag: String = "",
)

/**
 * 整部作品的下载信息（`album_download_2/<id>`）。
 *
 * **成功的判据是「拿到了下载地址」，不是 `status`** —— 实测成功响应里根本没有 `status`：
 * ```
 * {"title":"…","fileSize":"0.7 MB","download_url":"https://dl2025…/download_zip?md5=…","img_url":"…"}
 * ```
 * 而失败时才会出现 `status`：未登录是 `{"status":"0","msg":"請先登入"}`。
 * 按 `status == "1"` 判断会把成功的响应也当成失败（我踩过：界面报「暂时不能下载」）。
 */
@Serializable
data class DownloadPayload(
    /** 只在失败时出现。 */
    @Serializable(with = FlexStringOrNull::class) val status: String? = null,
    val msg: String? = null,
    val title: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    /** 形如 `"0.7 MB"`，展示用。 */
    @SerialName("fileSize") val fileSize: String? = null,
    @SerialName("img_url") val imgUrl: String? = null,
) {
    val isOk: Boolean get() = !downloadUrl.isNullOrBlank()
}
