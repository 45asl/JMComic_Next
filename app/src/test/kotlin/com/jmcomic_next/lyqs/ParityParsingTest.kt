package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.data.remote.JmJson
import com.jmcomic_next.lyqs.data.remote.dto.CreatorAuthor
import com.jmcomic_next.lyqs.data.remote.dto.CreatorEnvelope
import com.jmcomic_next.lyqs.data.remote.dto.CreatorPage
import com.jmcomic_next.lyqs.data.remote.dto.CreatorWork
import com.jmcomic_next.lyqs.data.remote.dto.CreatorWorkContent
import com.jmcomic_next.lyqs.data.remote.dto.CreatorWorkInfo
import com.jmcomic_next.lyqs.data.remote.dto.DownloadPayload
import com.jmcomic_next.lyqs.data.remote.dto.TagItem
import com.jmcomic_next.lyqs.data.remote.dto.TagPayload
import com.jmcomic_next.lyqs.data.remote.dto.WeekFilterPayload
import com.jmcomic_next.lyqs.data.remote.dto.WeekPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 新接入的几块（周刊 / 创作者库 / 收藏标签 / 下载）的解析测试。
 *
 * 里面的 JSON 都是**实测响应的原文截取**，不是手写的理想样例 ——
 * 这几处的坑恰恰在于真实数据与直觉不符：`title` 是空串、刊期 id 与期号不相等、
 * 两层封套里 `status` 一个回字符串一个回数字、标签字段叫 `tag` 而不是 `name`。
 */
class ParityParsingTest {

    /** `week`：刊期在 `categories` 里，能显示的是 `time`（`title` 实测为空串）。 */
    @Test
    fun `week payload reads issues from categories`() {
        val payload = JmJson.decodeFromString(WeekPayload.serializer(), """
            {"categories":[
              {"id":"259","title":"","time":"2026第258期09.25 - 09.18"},
              {"id":"258","title":"","time":"2026第257期09.18 - 09.11"}],
             "type":[{"id":"hanman","title":"韩漫"},{"id":"another","title":"其他"},{"id":"manga","title":"日漫"}]}
        """.trimIndent())
        assertEquals(2, payload.categories.size)
        assertEquals("259", payload.categories.first().id)
        // title 是空串，所以 label 必须回退到 time
        assertEquals("2026第258期09.25 - 09.18", payload.categories.first().label)
        assertEquals(listOf("hanman", "another", "manga"), payload.type.map { it.id })
        // 展示名的键名是 title —— 按 name 读会全变成 null，界面上只剩 hanman 这种原始 id
        // （这个 bug 是真机上看界面时发现的）
        assertEquals(listOf("韩漫", "其他", "日漫"), payload.type.map { it.label })
    }

    /** `week/filter`：`{total, list}`，元素就是普通漫画列表项。 */
    @Test
    fun `week filter payload is a normal comic list`() {
        val payload = JmJson.decodeFromString(WeekFilterPayload.serializer(), """
            {"total":1,"list":[{"id":"1474177","name":"無人島之主","author":"DAZZLING SLAY",
              "category":{"id":"5","title":"韓漫"},"update_at":1790382012}]}
        """.trimIndent())
        assertEquals(1, payload.total?.toIntOrNull())
        assertEquals("1474177", payload.list.single().id)
        assertEquals("1790382012", payload.list.single().updateAt)
    }

    /**
     * 创作者库是**两层封套**，而且两个接口的 `status` 类型不一致 ——
     * 画师回字符串 `"200"`、作品回数字 `200`。用宽容类型接住，且不拿它判断成败。
     */
    @Test
    fun `creator envelope tolerates string and number status`() {
        val authors = JmJson.decodeFromString(
            CreatorEnvelope.serializer(CreatorPage.serializer(CreatorAuthor.serializer())),
            """
            {"status":"200","data":{"total":"20844","content":[
              {"id":"7118","author_name":"SirensParadise","update_date":"183 天 前",
               "author_avatar":"/media/library/artists/7118/icon/18446886.gif",
               "background_image":"/media/library/artists/7118/banner/18446886.gif"}]}}
            """.trimIndent(),
        )
        assertEquals("200", authors.status)
        assertEquals(20844, authors.data?.total?.toIntOrNull())
        val author = authors.data?.content?.single()
        assertEquals("SirensParadise", author?.name)
        assertEquals("183 天 前", author?.updateDate)
        assertTrue(author?.avatar?.endsWith(".gif") == true)

        val works = JmJson.decodeFromString(
            CreatorEnvelope.serializer(CreatorPage.serializer(CreatorWork.serializer())),
            """
            {"status":200,"data":{"total":"2374092","content":[
              {"id":"1100557","work_image":"/media/library/album/1100557/thumb/album.jpg",
               "work_title":"Room Service - Alpha 0.1 | Public Release",
               "work_date":"2026-04-01 16:14:49","platform_name":"patreon",
               "author_name":"SirensParadise","author_id":"7118"}]}}
            """.trimIndent(),
        )
        assertEquals("200", works.status)
        val work = works.data?.content?.single()
        assertEquals("patreon", work?.platform)
        assertEquals("7118", work?.authorId)
    }

    /** 作品信息：作者、日期与相关作品。 */
    @Test
    fun `creator work info carries related works`() {
        val info = JmJson.decodeFromString(CreatorWorkInfo.serializer(), """
            {"work_date":"20728 天 前","author_name":"photonlanccer","work_title":"Honoka Close",
             "related_works":[{"id":"1342550","work_title":"Honoka Momo Bikini 7C",
               "work_image":"/media/library/album/1342550/thumb/album.jpg","platform_name":"fanbox"}]}
        """.trimIndent())
        assertEquals("photonlanccer", info.authorName)
        assertEquals("1342550", info.relatedWorks.single().id)
    }

    /**
     * 作品内容：**并非每个作品都有内容**（实测有 `total_page: 0`、`images: []` 的），
     * 界面要把它当「没有可看的内容」而不是错误。
     */
    @Test
    fun `creator work content may be empty but still parses`() {
        val empty = JmJson.decodeFromString(CreatorWorkContent.serializer(), """
            {"id":1478496,"name":"Honoka Close NO WATERMARK","total_page":0,"images":[],
             "content":"","addtime":1690918039,"adddt":"2023-08-02 03:27:19"}
        """.trimIndent())
        assertTrue(empty.images.isEmpty())
        assertEquals(0, empty.totalPage)
        assertEquals("2023-08-02 03:27:19", empty.addDate)

        val withImages = JmJson.decodeFromString(CreatorWorkContent.serializer(), """
            {"id":1,"name":"x","total_page":2,
             "images":[{"image":"/media/library/album/1/1.jpg"},{"image":"/media/library/album/1/2.jpg"}]}
        """.trimIndent())
        assertEquals(2, withImages.images.size)
    }

    /** 收藏标签的字段是 `tag`，不是 `name` 或 `id`。 */
    @Test
    fun `favorite tags use the tag field`() {
        val payload = JmJson.decodeFromString(TagPayload.serializer(), """
            {"list":[{"tag":"純愛"},{"tag":"NTR"}]}
        """.trimIndent())
        assertEquals(listOf("純愛", "NTR"), payload.list.map { it.tag })

        val single = JmJson.decodeFromString(TagItem.serializer(), """{"tag":"後宮"}""")
        assertEquals("後宮", single.tag)
    }

    /**
     * 下载：**需要登录，而且失败不是 401** ——
     * 实测未登录时是 HTTP 200 + `{"status":"0","msg":"請先登入"}`，
     * 所以判断必须落在 `status` 上；把这种响应当成成功会让界面给一个空链接。
     */
    @Test
    fun `download payload distinguishes not-logged-in`() {
        val denied = JmJson.decodeFromString(DownloadPayload.serializer(), """
            {"status":"0","msg":"請先登入"}
        """.trimIndent())
        assertFalse(denied.isOk)
        assertEquals("請先登入", denied.msg)
        assertNull(denied.downloadUrl)

        val ok = JmJson.decodeFromString(DownloadPayload.serializer(), """
            {"status":"1","title":"某作品","download_url":"https://example.com/a.zip"}
        """.trimIndent())
        assertTrue(ok.isOk)
        assertEquals("https://example.com/a.zip", ok.downloadUrl)
    }
}
