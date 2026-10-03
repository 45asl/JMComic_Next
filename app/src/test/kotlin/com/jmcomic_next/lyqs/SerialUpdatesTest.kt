package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.data.SerialUpdates
import com.jmcomic_next.lyqs.data.remote.dto.ListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「我追的连载里哪些更新了」的判定（1.5.3）。
 *
 * 这条判定最怕的是**误报**：如果没读过或时间解析不出来也被算成"有更新"，
 * 整个列表都会亮起标记，提示就没用了。所以下面重点钉住几种"不该算"的情况。
 */
class SerialUpdatesTest {

    private fun item(id: String, updateAt: String?) = ListItem(id = id, name = "作品 $id", updateAt = updateAt)

    @Test
    fun `seconds and milliseconds are both accepted`() {
        // 服务端实测给的是秒（10 位），但类型是 number，换成毫秒也不该出错
        assertEquals(1_790_992_577_000L, SerialUpdates.parseEpochMillis("1790992577"))
        assertEquals(1_790_992_577_000L, SerialUpdates.parseEpochMillis("1790992577000"))
    }

    @Test
    fun `unparseable timestamps are ignored rather than treated as updates`() {
        assertNull(SerialUpdates.parseEpochMillis(null))
        assertNull(SerialUpdates.parseEpochMillis(""))
        assertNull(SerialUpdates.parseEpochMillis("abc"))
        assertNull(SerialUpdates.parseEpochMillis("0"))
        assertNull(SerialUpdates.parseEpochMillis("-5"))
    }

    @Test
    fun `only works updated after my last read count`() {
        val readAt = mapOf("1" to 1_790_000_000_000L, "2" to 1_790_000_000_000L)
        val items = listOf(
            item("1", "1790000001"),   // 读之后又更新了 → 算
            item("2", "1789999999"),   // 读之前就更新过 → 不算
        )
        val updated = SerialUpdates.updated(items) { readAt[it] }
        assertEquals(listOf("1"), updated.map { it.id })
    }

    @Test
    fun `works I never read are not updates`() {
        // 没读过 = 没追。否则连载列表里每一部都会亮，提示失去意义
        val items = listOf(item("1", "1790000001"))
        assertTrue(SerialUpdates.updated(items) { null }.isEmpty())
    }

    @Test
    fun `equal timestamps are not an update`() {
        val t = 1_790_000_000_000L
        val items = listOf(item("1", "1790000000"))
        assertTrue(SerialUpdates.updated(items) { t }.isEmpty())
    }

    @Test
    fun `fingerprint ignores order so the same batch is notified once`() {
        // 指纹的用途是"同一批只通知一次"。列表顺序（服务端每次返回的顺序可能不同）
        // 不能影响它，否则同一批更新会被反复通知。
        val a = listOf(item("2", null), item("1", null))
        val b = listOf(item("1", null), item("2", null))
        assertEquals(SerialUpdates.fingerprint(a), SerialUpdates.fingerprint(b))
        // 批次变了就必须不同，否则新的更新会被当成"已经通知过"而漏掉
        assertTrue(SerialUpdates.fingerprint(a) != SerialUpdates.fingerprint(listOf(item("1", null))))
        assertEquals("", SerialUpdates.fingerprint(emptyList()))
    }

    @Test
    fun `items without update_at never count`() {
        val items = listOf(item("1", null))
        assertTrue(SerialUpdates.updated(items) { 0L }.isEmpty())
    }
}
