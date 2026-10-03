package com.jmcomic_next.lyqs.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 通知（1.5.3）。
 *
 * 结构以官方 Web 客户端源码为准（`NotificationList.tsx` 的通知项渲染、
 * `memberAction.ts` 的 `FETCH_GET_NOTIFICATIONS_LIST_THUNK`）：
 * `GET notifications {type, subType, page}` → `{code, data:{list, total}}`。
 *
 * **`content` 是多态的**：`comic_follow`（追更）给的是数组
 * `[{comicId, comicTitle, updateDate}]`，而 `site_notice`（站内通知）给的是
 * 一段 HTML 字符串。所以这里收成 [JsonElement]，由 [followedUpdates] 分流 ——
 * 硬解成列表会在站内通知上直接抛异常。
 */
@Serializable
data class NotificationItem(
    @Serializable(with = FlexStringOrNull::class) val id: String? = null,
    /** `comic_follow`（追更）或 `site_notice`（站内通知）。 */
    val type: String? = null,
    val date: String? = null,
    @Serializable(with = FlexBool::class) val read: Boolean = false,
    val title: String? = null,
    val content: JsonElement? = null,
) {
    /** 追更通知里指向的作品与更新日期。 */
    fun followedUpdates(): List<FollowedUpdate> {
        if (type != TYPE_COMIC_FOLLOW) return emptyList()
        val array = content as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return array.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            FollowedUpdate(
                comicId = obj["comicId"]?.jsonPrimitive?.contentOrNull(),
                comicTitle = obj["comicTitle"]?.jsonPrimitive?.contentOrNull(),
                updateDate = obj["updateDate"]?.jsonPrimitive?.contentOrNull(),
            )
        }
    }

    /** 站内通知的正文（HTML 字符串）；追更通知返回 null。 */
    fun siteNoticeHtml(): String? {
        if (type == TYPE_COMIC_FOLLOW) return null
        val prim = (content as? kotlinx.serialization.json.JsonPrimitive) ?: return null
        return prim.contentOrNull()
    }

    companion object {
        const val TYPE_COMIC_FOLLOW = "comic_follow"
        const val TYPE_SITE_NOTICE = "site_notice"
    }
}

/** 追更通知里的一条：哪部作品、什么时候更新的。 */
@Serializable
data class FollowedUpdate(
    @SerialName("comicId")
    @Serializable(with = FlexStringOrNull::class) val comicId: String? = null,
    @SerialName("comicTitle") val comicTitle: String? = null,
    @SerialName("updateDate") val updateDate: String? = null,
)

@Serializable
data class NotificationPayload(
    val list: List<NotificationItem> = emptyList(),
    val total: Int = 0,
    val code: Int = 0,
)

/**
 * 未读数量。
 *
 * 源码里界面同时用到「总数」和**按类型分**的数量（`unread[comic_follow]`、
 * `unread[site_notice]`），所以 `data` 既可能是数字也可能是一个对象 ——
 * 这里按对象收，缺字段时回退到 0，调用方只需要 [total] 或 [byType]。
 */
@Serializable
data class NotificationUnread(
    val all: Int = 0,
    @SerialName("comic_follow") val comicFollow: Int = 0,
    @SerialName("site_notice") val siteNotice: Int = 0,
    val code: Int = 0,
) {
    val total: Int get() = if (all > 0) all else comicFollow + siteNotice
}

/** `jsonPrimitive` 在非基础类型上会抛异常；通知里字段类型不保证，所以统一走这里。 */
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    runCatching { content }.getOrNull()
