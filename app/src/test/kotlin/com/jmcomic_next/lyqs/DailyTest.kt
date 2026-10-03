package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.data.Daily
import com.jmcomic_next.lyqs.data.remote.dto.DailyDay
import com.jmcomic_next.lyqs.data.remote.dto.DailyHistory
import com.jmcomic_next.lyqs.data.remote.dto.DailyHistoryOptions
import com.jmcomic_next.lyqs.data.remote.dto.DailyPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 签到的纯逻辑（1.5.4）。
 *
 * 这些判断最容易错的地方是**方向**：全签完判定写反会让按钮永远点得动，
 * 「已经签过了」判定漏掉会把一次正常操作报成失败。所以正反例都要钉。
 */
class DailyTest {

    private fun day(signed: Boolean) = DailyDay(signed = signed)

    @Test
    fun `counts signed days and total days`() {
        val record = listOf(
            listOf(day(true), day(true), day(false)),
            listOf(day(true), day(false)),
        )
        assertEquals(3, Daily.signedCount(record))
        assertEquals(5, Daily.totalDays(record))
    }

    @Test
    fun `daily payload survives fields whose type varies`() {
        // 与通知同一类问题：这套接口字段类型会变。名字/code 给成数字时
        // 不该让整条响应解析失败（通知那边真机上就是这么炸的）。
        val raw = "{\"daily_id\":12345,\"event_name\":678,\"code\":\"200\"," +
            "\"background_phone\":999,\"record\":[[{\"signed\":\"1\",\"bonus\":5,\"date\":1790992577}]]}"
        val payload = kotlinx.serialization.json.Json { isLenient = true; ignoreUnknownKeys = true }
            .decodeFromString(DailyPayload.serializer(), raw)
        assertEquals("12345", payload.dailyId)
        assertEquals("678", payload.eventName)
        assertEquals(200, payload.code)
        assertEquals("999", payload.backgroundPhone)
        assertEquals(1, Daily.signedCount(payload.record))
        assertEquals("1790992577", payload.record[0][0].date)
    }

    @Test
    fun `history calendar tolerates mixed field types`() {
        // 与通知同一类问题：这套接口的类型会变。年份给成数字、图片路径给成数字，
        // 都不该让整条响应失败 —— 读不出来只是少显示一条，而不是整页报错。
        val json = kotlinx.serialization.json.Json { isLenient = true; ignoreUnknownKeys = true }
        val options = json.decodeFromString(
            DailyHistoryOptions.serializer(),
            "{\"list\":[{\"title\":2026},{\"title\":\"2025\"}]}",
        )
        assertEquals(listOf("2026", "2025"), options.list.mapNotNull { it.titleText })

        val history = json.decodeFromString(
            DailyHistory.serializer(),
            "{\"list\":[{\"img\":123,\"date\":\"2026-10-01\",\"bonus\":5}],\"total\":\"1\"}",
        )
        assertEquals("123", history.list[0].imgText)
        assertEquals("2026-10-01", history.list[0].dateText)
        assertEquals("5", history.list[0].bonusText)
    }

    @Test
    fun `complete only when every day of every week is signed`() {
        assertTrue(Daily.isComplete(listOf(listOf(day(true), day(true)), listOf(day(true)))))
        // 只差一天就不算完成 —— 这正是"按钮该不该禁用"的依据
        assertFalse(Daily.isComplete(listOf(listOf(day(true), day(true)), listOf(day(false)))))
    }

    @Test
    fun `an empty calendar is not complete`() {
        // 没有活动数据时说"已签完"是在告诉用户一件不成立的事，而且按钮会被禁用
        assertFalse(Daily.isComplete(emptyList()))
        assertEquals(0, Daily.totalDays(emptyList()))
    }

    @Test
    fun `already-checked-in is recognised in both traditional and simplified`() {
        // 官方源码里只判繁体（msg.includes("已經簽到過了")），我们的用户两种都会遇到
        assertTrue(Daily.isAlreadyChecked("您今天已經簽到過了"))
        assertTrue(Daily.isAlreadyChecked("您今天已经签到过了"))
        assertTrue(Daily.isAlreadyChecked("已簽到"))
        assertFalse(Daily.isAlreadyChecked("簽到成功"))
        assertFalse(Daily.isAlreadyChecked(null))
        assertFalse(Daily.isAlreadyChecked(""))
    }
}
