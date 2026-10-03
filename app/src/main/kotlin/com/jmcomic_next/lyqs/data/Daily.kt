package com.jmcomic_next.lyqs.data

import com.jmcomic_next.lyqs.data.remote.dto.DailyDay

/**
 * 签到里的**纯逻辑**（1.5.4）。
 *
 * 抽出来是因为这些判断全都可以脱离 Android 与网络单独验证，
 * 而它们恰好是最容易写错的地方：**「全部签完」判定写反会让按钮永远点得动，
 * 「已经签过了」判定漏掉会把一次正常操作报成失败**。
 *
 * 依据官方 Web 客户端（`Daily.tsx`）：
 * - 全部签完：`record.every(week => week.every(day => day.signed))`
 * - 重复打卡：响应 `msg` 里含「已經簽到過了」（**注意是繁体**，所以要同时认简繁）
 */
object Daily {

    /** 已签的天数。 */
    fun signedCount(record: List<List<DailyDay>>): Int =
        record.sumOf { week -> week.count { it.signed } }

    /** 日历里一共有多少天。 */
    fun totalDays(record: List<List<DailyDay>>): Int = record.sumOf { it.size }

    /**
     * 是否**全部签完**。
     *
     * 空日历返回 false：没有活动数据时不该显示"已签完"，
     * 否则界面会告诉用户一件不成立的事（而且按钮会被禁用，用户点不了）。
     */
    fun isComplete(record: List<List<DailyDay>>): Boolean =
        record.isNotEmpty() && record.all { week -> week.all { it.signed } }

    /**
     * 响应是否表示"今天已经签过了"。
     *
     * 官方只判繁体「已經簽到過了」；这里同时认简繁，并且不依赖具体措辞顺序 ——
     * 只要出现"已（經）签（到）过"这个意思就按提示处理，而不是当成错误报给用户。
     */
    fun isAlreadyChecked(message: String?): Boolean {
        val m = message ?: return false
        return m.contains("已簽到") || m.contains("已签到") ||
            m.contains("已經簽到") || m.contains("已经签到")
    }
}
