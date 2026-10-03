package com.jmcomic_next.lyqs.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * 通知（1.5.3）。结构以官方 Web 客户端源码为准（`NotificationList.tsx` +
 * `memberAction.ts` 的 `FETCH_GET_NOTIFICATIONS_LIST_THUNK`）。
 *
 * ## 为什么每个字段都收成 [JsonElement]
 *
 * 这里是踩过坑之后的写法。第一版按"看起来对"的类型收（`date: String?`、
 * `read: FlexBool`、`total: Int`），结果在真机上**解析失败** ——
 * 服务端对 `date` 给的是数字、`total` 可能是字符串，而**同一个字段在不同响应里
 * 类型还会变**（这套接口的老毛病，项目的 Flex* 序列化器就是为它写的）。
 *
 * 与其一个个猜哪几个字段会变，不如把不确定性收在一个地方：
 * 字段全部按 [JsonElement] 原样接住，取值统一走下面这几个 accessor。
 * 类型猜错的最坏后果从"整条响应解析失败"降级成"某个字段读不出来"。
 */
@Serializable
data class NotificationItem(
    val id: JsonElement? = null,
    /** `comic_follow`（追更）或 `site_notice`（站内通知）。 */
    val type: JsonElement? = null,
    val date: JsonElement? = null,
    val read: JsonElement? = null,
    val title: JsonElement? = null,
    /** 追更给数组 `[{comicId, comicTitle, updateDate}]`；站内通知给 HTML 字符串。 */
    val content: JsonElement? = null,
) {
    val idText: String? get() = id.asText()
    val typeText: String? get() = type.asText()
    val dateText: String? get() = date.asText()
    val titleText: String? get() = title.asText()
    val isRead: Boolean get() = read.asBool()

    /** 追更通知里指向的作品与更新日期；站内通知返回空表。 */
    fun followedUpdates(): List<FollowedUpdate> {
        if (typeText != TYPE_COMIC_FOLLOW) return emptyList()
        val array = content as? JsonArray ?: return emptyList()
        return array.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            FollowedUpdate(
                // 传原始元素，类型判断留给 accessor —— 这里取值同样不猜类型
                comicId = obj["comicId"],
                comicTitle = obj["comicTitle"],
                updateDate = obj["updateDate"],
            )
        }
    }

    /** 站内通知的正文（原文 HTML）；追更通知返回 null。 */
    fun siteNoticeHtml(): String? {
        if (typeText == TYPE_COMIC_FOLLOW) return null
        return (content as? JsonPrimitive)?.contentOrNull()
    }

    companion object {
        const val TYPE_COMIC_FOLLOW = "comic_follow"
        const val TYPE_SITE_NOTICE = "site_notice"
    }
}

/** 追更通知里的一条：哪部作品、什么时候更新的。同样按"类型不保证"处理。 */
@Serializable
data class FollowedUpdate(
    @SerialName("comicId") val comicId: JsonElement? = null,
    @SerialName("comicTitle") val comicTitle: JsonElement? = null,
    @SerialName("updateDate") val updateDate: JsonElement? = null,
) {
    val comicIdText: String? get() = comicId.asText()
    val comicTitleText: String? get() = comicTitle.asText()
    val updateDateText: String? get() = updateDate.asText()
}

/**
 * 一页通知。**不是**直接反序列化出来的 —— `data` 的形态会变，
 * 由 [from] 按官方源码那两条分支解释：
 *
 * ```ts
 * const list = Array.isArray(data) ? data : data?.list ?? [];
 * const total = Array.isArray(data) ? 0 : Number(data?.total) || 0;
 * ```
 *
 * 这就是"仍然解析失败"的根因：服务端**有时给裸数组**（与 `promote` 同形），
 * 而我最初只按 `{list, total}` 对象解 —— 给数组时整条响应就炸了。
 */
data class NotificationPage(
    val list: List<NotificationItem> = emptyList(),
    val total: Int = 0,
) {
    companion object {
        private val lenient = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }

        fun from(element: JsonElement?): NotificationPage = when (element) {
            null -> NotificationPage()
            // 裸数组：整段就是列表，没有总数（与源码一致）
            is JsonArray -> NotificationPage(element.mapNotNull(::item))
            is JsonObject -> NotificationPage(
                list = (element["list"] as? JsonArray)?.mapNotNull(::item) ?: emptyList(),
                total = element["total"].asInt() ?: 0,
            )
            else -> NotificationPage()
        }

        /** 单条坏数据只丢它自己，不牵连整页 —— 列表接口尤其该这样。 */
        private fun item(element: JsonElement): NotificationItem? =
            runCatching { lenient.decodeFromJsonElement(NotificationItem.serializer(), element) }
                .getOrNull()
    }
}

/**
 * 未读数量。`data` **既可能是数字**（未读总数）**也可能是对象**（按类型分），
 * 所以整段收成 [JsonElement] 再解释。
 */
data class NotificationUnread(
    val total: Int = 0,
    private val counts: Map<String, Int> = emptyMap(),
) {
    fun byType(type: String): Int = counts[type] ?: 0

    companion object {
        fun from(element: JsonElement?): NotificationUnread = when (element) {
            null -> NotificationUnread()
            is JsonObject -> {
                val counts = element.mapNotNull { (k, v) -> v.asInt()?.let { k to it } }.toMap()
                val all = counts["all"] ?: 0
                // 没有 all 时用分项相加兜底（源码里 unreadCount 与 unread[type] 是两个数）
                NotificationUnread(
                    total = if (all > 0) all else counts.values.sum(),
                    counts = counts,
                )
            }
            else -> NotificationUnread(total = element.asInt() ?: 0)
        }
    }
}

/* ---- 取值兜底：类型不保证，读不出来一律当"没有"，绝不抛 ---- */

private fun JsonElement?.asText(): String? = when (this) {
    null -> null
    is JsonPrimitive -> contentOrNull()?.takeIf { it.isNotBlank() }
    else -> null
}

private fun JsonElement?.asInt(): Int? = asText()?.trim()?.toDoubleOrNull()?.toInt()
    ?: asText()?.trim()?.toLongOrNull()?.toInt()

private fun JsonElement?.asBool(): Boolean {
    val t = asText()?.lowercase() ?: return false
    return t in setOf("1", "true", "yes", "y")
}

private fun JsonPrimitive.contentOrNull(): String? = runCatching { content }.getOrNull()
