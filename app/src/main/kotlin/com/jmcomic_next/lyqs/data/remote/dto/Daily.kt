package com.jmcomic_next.lyqs.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable

/**
 * 签到（1.5.4）。
 *
 * 结构以官方 Web 客户端源码为准（`Daily.tsx` + `memberAction.ts` 的
 * `FETCH_GET_DAILY_THUNK`）：`GET daily` 返回一个活动，里面带
 * `daily_id`（打卡必须带）、`event_name`、活动背景图，以及 `record` ——
 * **按周分组的日历**，每天一个 `{signed, bonus, date}`。
 *
 * 服务端给的是「一周七天的数组，外面再套一层周」，所以类型是
 * `List<List<DailyDay>>`；界面按周铺格子即可。
 */
@Serializable
data class DailyDay(
    /** 官方用 `signed === true` 判断，但字段类型在 JS 里没有保证 —— 用 FlexBool 兜住字符串 "1"。 */
    @Serializable(with = FlexBool::class) val signed: Boolean = false,
    /** 当天奖励。类型不保证（数字/字符串都见过），统一按字符串收进来再解析。 */
    @Serializable(with = FlexStringOrNull::class) val bonus: String? = null,
    @Serializable(with = FlexStringOrNull::class) val date: String? = null,
)

@Serializable
data class DailyPayload(
    @SerialName("daily_id")
    @Serializable(with = FlexStringOrNull::class) val dailyId: String? = null,
    // 名字、code、msg 都按"类型不保证"收 —— 这正是通知那边踩过的坑：
    // 同一字段在不同响应里可能是字符串或数字，严格类型会让**整条响应**解析失败。
    @SerialName("event_name")
    @Serializable(with = FlexStringOrNull::class) val eventName: String? = null,
    /** 活动背景图路径，要拼 `setting` 里的图床主机。 */
    @SerialName("background_phone")
    @Serializable(with = FlexStringOrNull::class) val backgroundPhone: String? = null,
    /** 按周分组的签到记录。 */
    val record: List<List<DailyDay>> = emptyList(),
    @Serializable(with = FlexInt::class) val code: Int = 0,
    @Serializable(with = FlexStringOrNull::class) val msg: String? = null,
)

/** `daily_chk` 的响应：只要 `code` 与 `msg`（"已經簽到過了" 就在 msg 里）。 */
@Serializable
data class DailyCheckResult(
    @Serializable(with = FlexInt::class) val code: Int = 0,
    @Serializable(with = FlexStringOrNull::class) val msg: String? = null,
)

/**
 * 签到**历史日历**（1.5.4）。
 *
 * 与"今天打卡"是两件事（源码里分别是 `Daily.tsx` 与 `DailyList.tsx`）：
 * 先 `GET daily_list {user_id}` 拿到**可选的年份**，再用
 * `POST daily_list/filter {data:<年份>}` 拿那一年的记录 —— 每条带一张图 `img`，
 * 点开看大图。
 *
 * 字段同样按"类型不保证"收：这套接口给过数字也給过字符串。
 */
@Serializable
data class DailyHistoryOptions(
    val list: List<Option> = emptyList(),
) {
    @Serializable
    data class Option(val title: JsonElement? = null) {
        val titleText: String? get() = titleTextOrNull(title)
    }
}

@Serializable
data class DailyHistory(
    val list: List<Entry> = emptyList(),
    val total: JsonElement? = null,
) {
    @Serializable
    data class Entry(
        /** 源码里拿它当列表 key，也当图片加载失败时的替代文字。 */
        val id: JsonElement? = null,
        /** 记录的月份，源码在图上以"N月"角标显示。 */
        val month: JsonElement? = null,
        val img: JsonElement? = null,
        val date: JsonElement? = null,
        val title: JsonElement? = null,
        val bonus: JsonElement? = null,
    ) {
        val idText: String? get() = titleTextOrNull(id)
        val monthText: String? get() = titleTextOrNull(month)
        val imgText: String? get() = titleTextOrNull(img)
        val dateText: String? get() = titleTextOrNull(date)
        val titleText: String? get() = titleTextOrNull(title)
        val bonusText: String? get() = titleTextOrNull(bonus)
    }
}

/** 与 `Notifications.kt` 里同名工具一致：读不出来就当没有，绝不抛。 */
internal fun titleTextOrNull(el: JsonElement?): String? = when (el) {
    null -> null
    is kotlinx.serialization.json.JsonPrimitive -> runCatching { el.content }.getOrNull()?.takeIf { it.isNotBlank() }
    else -> null
}
