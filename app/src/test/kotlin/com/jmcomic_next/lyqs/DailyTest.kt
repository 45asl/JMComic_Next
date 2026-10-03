package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.data.Daily
import com.jmcomic_next.lyqs.data.remote.dto.DailyDay
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
