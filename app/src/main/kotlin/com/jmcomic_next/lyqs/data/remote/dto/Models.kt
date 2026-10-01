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

/** 「查看更多」响应（`InterFace.ts` 的 `MoreListResponse.data`）。数组键是 `list`。 */
@Serializable
data class MoreListPayload(
    @Serializable(with = FlexStringOrNull::class) val total: String? = null,
    val list: List<ListItem> = emptyList(),
)

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
