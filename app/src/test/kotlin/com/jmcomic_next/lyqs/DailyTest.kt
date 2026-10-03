package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.data.Daily
import com.jmcomic_next.lyqs.data.remote.dto.DailyDay
import com.jmcomic_next.lyqs.data.remote.dto.DailyHistory
import com.jmcomic_next.lyqs.data.remote.dto.DailyHistoryOptions
import com.jmcomic_next.lyqs.data.remote.dto.DailyPayload
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        val payload = kotlinx.serialization.json.TestJson
            .decodeFromString(DailyPayload.serializer(), raw)
        assertEquals("12345", payload.dailyId)
        assertEquals("678", payload.eventName)
        assertEquals(200, payload.code)
        assertEquals("999", payload.backgroundPhone)
        assertEquals(1, Daily.signedCount(payload.record))
        assertEquals("1790992577", payload.record[0][0].date)
    }

    // ---- 以下三条用的是**真实接口响应原文**（我用测试账号直接调接口抓下来的），
    //      不是我想象的结构。签到接口的坑都在这些细节里。 ----

    @Test
    fun `real daily payload decodes`() {
        // 真实响应要点：daily_id 是**数字**；signed 会出现 **null**（不只是 true/false）；
        // bonus 是**布尔**而不是奖励数字；date 只是"01"这样的日，不是完整日期。
        val raw = "{\"daily_id\":73,\"three_days_coin\":\"150\",\"seven_days_coin\":\"350\"," +
            "\"event_name\":\"10月-「来都来了」\",\"background_phone\":\"/media/logo/phone/10PH.jpg\"," +
            "\"currentProgress\":\"0%\",\"record\":[" +
            "[{\"date\":\"01\",\"signed\":false,\"bonus\":true},{\"date\":\"02\",\"signed\":null,\"bonus\":true}," +
            "{\"date\":\"03\",\"signed\":true,\"bonus\":true},{\"date\":\"04\",\"signed\":null,\"bonus\":false}," +
            "{\"date\":\"05\",\"signed\":null,\"bonus\":false},{\"date\":\"06\",\"signed\":null,\"bonus\":false}," +
            "{\"date\":\"07\",\"signed\":null,\"bonus\":false}]," +
            "[{\"date\":\"08\",\"signed\":null,\"bonus\":false}]]}"
        val payload = TestJson
            .decodeFromString(DailyPayload.serializer(), raw)
        assertEquals("73", payload.dailyId)
        assertEquals("10月-「来都来了」", payload.eventName)
        assertEquals(8, Daily.totalDays(payload.record))
        // 只有 signed==true 才算已签：null 与 false 都不算（否则进度会虚高）
        assertEquals(1, Daily.signedCount(payload.record))
        assertFalse(Daily.isComplete(payload.record))
    }

    @Test
    fun `real history years default to the newest, not the first`() {
        // 真实响应：从旧到新。取第一个会停在三年前 —— 这是我照真实数据改掉的一个 bug。
        val years = listOf("2024", "2025", "2026")
        assertEquals("2026", Daily.defaultHistoryYear(years, 2026))
        // 今年不在列表里时取最大的一年，而不是第一个
        assertEquals("2026", Daily.defaultHistoryYear(years, 2030))
        assertEquals("2025", Daily.defaultHistoryYear(listOf("2024", "2025"), 2000))
        assertNull(Daily.defaultHistoryYear(emptyList(), 2026))
    }

    @Test
    fun `real history entries may have no image at all`() {
        // 真实响应里 img 全是 null。界面**不能因此丢掉这一格** ——
        // 源码里月份角标写在"有没有图"的判断之外，所以这一格必须还在。
        val raw = "{\"list\":[{\"id\":\"64\",\"year\":\"2026\",\"month\":\"1\",\"img\":null}," +
            "{\"id\":\"73\",\"year\":\"2026\",\"month\":\"10\",\"img\":null}]}"
        val history = TestJson
            .decodeFromString(DailyHistory.serializer(), raw)
        assertEquals(2, history.list.size)
        assertEquals("64", history.list[0].idText)
        assertEquals("1", history.list[0].monthText)
        assertNull(history.list[0].imgText)
    }

    @Test
    fun `history calendar tolerates mixed field types`() {
        // 与通知同一类问题：这套接口的类型会变。年份给成数字、图片路径给成数字，
        // 都不该让整条响应失败 —— 读不出来只是少显示一条，而不是整页报错。
        val json = kotlinx.serialization.json.TestJson
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
    fun `knows whether today is already signed`() {
        // 真实响应里 date 只是"几号"（"01"），所以用当月日号去对
        val record = listOf(
            listOf(DailyDay(signed = true, date = "01"), DailyDay(signed = false, date = "02")),
            listOf(DailyDay(signed = false, date = "15")),
        )
        assertTrue(Daily.isSignedToday(record, 1))
        assertFalse(Daily.isSignedToday(record, 2))
        assertFalse(Daily.isSignedToday(record, 15))
        // 对不上任何一天时返回 false —— 宁可让按钮可点，也不要错误地显示"已签"
        assertFalse(Daily.isSignedToday(record, 28))
        assertFalse(Daily.isSignedToday(emptyList(), 1))
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

    /** 共享一份：编译器点名"每次使用都创建会很慢"，测试里也一样（1.7.0 清零全部警告）。 */
    private val TestJson = Json { isLenient = true; ignoreUnknownKeys = true; coerceInputValues = true }
}
