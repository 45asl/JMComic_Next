package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.data.remote.dto.NotificationItem
import com.jmcomic_next.lyqs.data.remote.dto.NotificationPage
import com.jmcomic_next.lyqs.data.remote.dto.NotificationUnread
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知项（1.5.3）。
 *
 * 这里最危险的是 `content` **多态**：追更通知给数组、站内通知给 HTML 字符串。
 * 硬解成 `List<FollowedUpdate>` 会在站内通知上抛异常 —— 而站内通知恰恰是
 * 用户最容易先收到的一类（欢迎/公告）。所以两种都要钉。
 */
class NotificationItemTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `comic_follow exposes the updated works`() {
        val raw = """
            {"id":"9","type":"comic_follow","date":"2026-10-01","read":false,
             "content":[{"comicId":1476217,"comicTitle":"某部作品","updateDate":"2026-09-30"},
                        {"comicId":"1478087","comicTitle":"另一部","updateDate":"2026-09-29"}]}
        """.trimIndent()
        val item = json.decodeFromString(NotificationItem.serializer(), raw)
        val ups = item.followedUpdates()
        assertEquals(2, ups.size)
        // 数字型 comicId 也要能读成字符串（服务端两种都给过）
        assertEquals("1476217", ups[0].comicIdText)
        assertEquals("某部作品", ups[0].comicTitleText)
        assertEquals("1478087", ups[1].comicIdText)
        assertNull("追更通知没有 HTML 正文", item.siteNoticeHtml())
    }

    @Test
    fun `site_notice exposes html and never tries to parse it as a list`() {
        val raw = """
            {"id":"1","type":"site_notice","date":"2026-10-02","read":true,
             "title":"公告","content":"<p>服务器维护</p>"}
        """.trimIndent()
        val item = json.decodeFromString(NotificationItem.serializer(), raw)
        // 站内通知的正文是**原文 HTML**（要保留标签，界面按富文本渲染），
        // 所以这里断言"包含正文"而不是"等于纯文本"
        val html = item.siteNoticeHtml()
        assertTrue("应返回原文 HTML：$html", html != null && html.contains("服务器维护"))
        assertTrue("站内通知不该产出追更条目", item.followedUpdates().isEmpty())
    }

    @Test
    fun `fields whose type is not guaranteed never break parsing`() {
        // 回归测试：真机上出现过"解析失败"，根因就是这些字段的类型会变
        //（date 是数字、title 是数字、read 是 0/1）。整条响应不该因此解码失败。
        val raw = "{\"id\":123,\"type\":\"comic_follow\",\"date\":1790992577,\"read\":0," +
            "\"title\":456,\"content\":[{\"comicId\":789,\"comicTitle\":123,\"updateDate\":1790992577}]}"
        val item = json.decodeFromString(NotificationItem.serializer(), raw)
        assertEquals("123", item.idText)
        assertEquals("comic_follow", item.typeText)
        assertEquals("1790992577", item.dateText)
        assertEquals("456", item.titleText)
        assertFalse(item.isRead)
        val up = item.followedUpdates().single()
        assertEquals("789", up.comicIdText)
        assertEquals("123", up.comicTitleText)
        assertEquals("1790992577", up.updateDateText)
    }

    @Test
    fun `a page tolerates both a bare array and an object`() {
        // 真机"解析失败"的根因：服务端有时把 data 给成**裸数组**（与 promote 同形），
        // 我最初只按 {list,total} 对象解，于是整条响应炸掉。
        // 官方源码里就是这么兜的：Array.isArray(data) ? data : data?.list ?? []
        val bareArray = Json.parseToJsonElement(
            """[{"id":1,"type":"site_notice","content":"<p>a</p>"},
                {"id":2,"type":"comic_follow","content":[{"comicId":9}]}]""",
        )
        val fromArray = NotificationPage.from(bareArray)
        assertEquals(2, fromArray.list.size)
        assertEquals(0, fromArray.total)   // 裸数组没有总数，与源码一致
        assertEquals("9", fromArray.list[1].followedUpdates().single().comicIdText)

        val asObject = Json.parseToJsonElement(
            """{"list":[{"id":3,"type":"site_notice"}],"total":42}""",
        )
        val fromObject = NotificationPage.from(asObject)
        assertEquals(1, fromObject.list.size)
        assertEquals(42, fromObject.total)

        // 完全不是这两种形态时也不能抛，给一页空的
        assertEquals(0, NotificationPage.from(Json.parseToJsonElement("\"oops\"")).list.size)
        assertEquals(0, NotificationPage.from(null).list.size)
    }

    @Test
    fun `a single bad item does not lose the whole page`() {
        // 一条坏数据只丢它自己：列表接口尤其该这样，不能因为一条脏数据整页空白
        val page = NotificationPage.from(
            Json.parseToJsonElement("""[{"id":1,"type":"site_notice"}, 7, "junk"]"""),
        )
        assertEquals(1, page.list.size)
    }

    @Test
    fun `unread count accepts both a number and an object`() {
        // 源码里 unread 是对象、unreadCount 是数字，两处都可能出现，两种都得认
        assertEquals(7, NotificationUnread.from(Json.parseToJsonElement("7")).total)
        val asObject = NotificationUnread.from(
            Json.parseToJsonElement("""{"all":9,"comic_follow":5,"site_notice":4}"""),
        )
        assertEquals(9, asObject.total)
        assertEquals(5, asObject.byType("comic_follow"))
        // 只有分类型、没有 all 时用分项相加兜底
        assertEquals(5, NotificationUnread.from(
            Json.parseToJsonElement("""{"comic_follow":3,"site_notice":2}"""),
        ).total)
        assertEquals(0, NotificationUnread.from(null).total)
    }

    @Test
    fun `missing or odd content never throws`() {
        // 字段类型没有保证：content 缺失、是数字、是空数组，都得安静地返回空
        listOf(
            """{"id":"1","type":"comic_follow"}""",
            """{"id":"1","type":"comic_follow","content":3}""",
            """{"id":"1","type":"comic_follow","content":[]}""",
            """{"id":"1"}""",
        ).forEach { raw ->
            val item = json.decodeFromString(NotificationItem.serializer(), raw)
            assertTrue("$raw 不该产出追更条目", item.followedUpdates().isEmpty())
            assertNull(item.siteNoticeHtml())
        }
    }
}
