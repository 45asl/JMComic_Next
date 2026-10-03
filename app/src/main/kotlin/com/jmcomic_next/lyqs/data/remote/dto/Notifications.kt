package com.jmcomic_next.lyqs.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

@Serializable
data class NotificationPayload(
    val list: List<NotificationItem> = emptyList(),
    val total: JsonElement? = null,
) {
    val totalCount: Int get() = total.asInt() ?: list.size
}

/**
 * 未读数量。
 *
 * `data` 既可能是**数字**（未读总数）也可能是一个**对象**（按类型分），
 * 所以这里整个收成 [JsonElement]，两种形态都能读出值：
 * 是数字就直接当总数；是对象就取 `all`（缺了就用 `comic_follow + site_notice` 兜）。
 */
@Serializable
data class NotificationUnread(
    val data: JsonElement? = null,
) {
    val total: Int get() = when (val d = data) {
        null -> 0
        is JsonObject -> {
            val all = d["all"].asInt() ?: 0
            if (all > 0) all else (d["comic_follow"].asInt() ?: 0) + (d["site_notice"].asInt() ?: 0)
        }
        else -> d.asInt() ?: 0
    }

    fun byType(type: String): Int =
        (data as? JsonObject)?.get(type).asInt() ?: 0
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
