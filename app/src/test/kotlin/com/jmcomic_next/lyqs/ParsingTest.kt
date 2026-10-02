package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.data.remote.Envelope
import com.jmcomic_next.lyqs.data.remote.JmJson
import com.jmcomic_next.lyqs.data.remote.dto.AlbumDetail
import com.jmcomic_next.lyqs.data.remote.dto.CommentItem
import com.jmcomic_next.lyqs.data.remote.dto.FavoriteListPayload
import com.jmcomic_next.lyqs.data.remote.dto.ForumPayload
import com.jmcomic_next.lyqs.data.remote.dto.HistoryPayload
import com.jmcomic_next.lyqs.data.remote.dto.ListItem
import com.jmcomic_next.lyqs.data.remote.dto.MoreListPayload
import com.jmcomic_next.lyqs.data.remote.dto.PagedList
import com.jmcomic_next.lyqs.data.remote.dto.SearchPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 解析层的单元测试。
 *
 * 这个 API 没有文档，类型还不稳定（同一个字段时而数字时而字符串、缺字段、多字段），
 * 所以解析一律宽容 —— 而这些「宽容」正是最需要钉住的地方：一旦有人换了序列化器，
 * 表现会是某个页面**静默**空掉，而不是抛异常。
 */
class ParsingTest {

    /** 类型混用：`id` 是数字还是字符串、`update_at` 是数字还是字符串，都得吃下。 */
    @Test
    fun `list item tolerates mixed types`() {
        val numberId = JmJson.decodeFromString(ListItem.serializer(), """
            {"id":1478339,"name":"x","author":"a","update_at":1790825920,
             "liked":1,"is_favorite":"1","category":{"id":5,"title":"韩漫"}}
        """.trimIndent())
        assertEquals("1478339", numberId.id)
        assertEquals("1790825920", numberId.updateAt)
        assertTrue(numberId.liked)
        assertTrue(numberId.isFavorite)
        assertEquals("5", numberId.category?.id)

        val stringId = JmJson.decodeFromString(ListItem.serializer(), """{"id":"1478339"}""")
        assertEquals("1478339", stringId.id)
    }

    /** 服务端偶尔回 `category_sub: {id: null}` —— 不能因此整条解析失败。 */
    @Test
    fun `null sub category is fine`() {
        val item = JmJson.decodeFromString(ListItem.serializer(), """
            {"id":"1","category_sub":{"id":null,"title":null}}
        """.trimIndent())
        assertNull(item.categorySub?.id)
    }

    /**
     * 失败响应的文案在 `errorMsg` 而不是 `msg`。
     *
     * 实测原文：`{"code":401,"data":[],"errorMsg":"无效的用户名和\/或密码!"}`。
     * 这条断言守着「用户能看到服务端说的话」这件事 —— 丢掉它，界面就只能自己编。
     */
    @Test
    fun `envelope prefers errorMsg over msg`() {
        val onlyErrorMsg = JmJson.decodeFromString(Envelope.serializer(), """
            {"code":401,"data":[],"errorMsg":"无效的用户名和\/或密码!"}
        """.trimIndent())
        assertEquals(401, onlyErrorMsg.code)
        assertEquals("无效的用户名和/或密码!", onlyErrorMsg.message)

        val both = JmJson.decodeFromString(Envelope.serializer(), """
            {"code":200,"msg":"来自 msg","errorMsg":"来自 errorMsg"}
        """.trimIndent())
        assertEquals("来自 errorMsg", both.message)

        val blank = JmJson.decodeFromString(Envelope.serializer(), """{"code":200,"msg":"","errorMsg":"  "}""")
        assertNull(blank.message)
    }

    /**
     * 搜索：数组键是 `content` 而不是 `list`，且 `total` 是字符串。
     */
    @Test
    fun `search payload uses content and string total`() {
        val payload = JmJson.decodeFromString(SearchPayload.serializer(), """
            {"search_query":"abc","total":"10000","content":[{"id":"1"},{"id":"2"}]}
        """.trimIndent())
        assertEquals("10000", payload.total)
        assertEquals(2, payload.content.size)
    }

    /** 按编号精确检索时服务端只回 `redirect_aid`，客户端应直接跳详情。 */
    @Test
    fun `search payload carries redirect aid`() {
        val payload = JmJson.decodeFromString(SearchPayload.serializer(), """{"redirect_aid":1478339}""")
        assertEquals("1478339", payload.redirectAid)
    }

    /**
     * 连载更新的末页：**响应体里只有 `{"error":"没有资料"}`**。
     *
     * 这一条是「能不能判断到底」的全部依据 —— 解析器若把它当成失败，界面就会报错而不是收尾。
     */
    @Test
    fun `weekly last page is an empty list not an error`() {
        val payload = JmJson.decodeFromString(MoreListPayload.serializer(), """{"error":"没有资料"}""")
        assertTrue(payload.list.isEmpty())
        assertNull(payload.total)
    }

    /** 分区更多：`total` 是字符串，每页 30 条。 */
    @Test
    fun `more list payload reads string total`() {
        val payload = JmJson.decodeFromString(MoreListPayload.serializer(), """
            {"total":"133","list":[{"id":"569509"}]}
        """.trimIndent())
        assertEquals("133", payload.total)
        assertEquals(1, payload.list.size)
    }

    /**
     * **总数缺失时不许用「本页条数」冒充。**
     *
     * 用本页条数冒充会让分页判断 `已加载数 >= total` 在第一页就成立 ——
     * 界面表现为「永远只有 20 条，还写着共 20 项」。这条断言就是钉住那个修复。
     */
    @Test
    fun `missing total stays unknown instead of page size`() {
        val favorites = JmJson.decodeFromString(FavoriteListPayload.serializer(), """
            {"list":[{"id":"1"},{"id":"2"},{"id":"3"}]}
        """.trimIndent())
        assertEquals(0, favorites.totalCount)

        val history = JmJson.decodeFromString(HistoryPayload.serializer(), """{"list":[{"id":"1"}]}""")
        assertEquals(0, history.totalCount)

        val forum = JmJson.decodeFromString(ForumPayload.serializer(), """{"list":[]}""")
        assertEquals(0, forum.totalCount)

        // 给了就照用（可能是字符串）
        val withTotal = JmJson.decodeFromString(FavoriteListPayload.serializer(), """{"list":[],"total":"37"}""")
        assertEquals(37, withTotal.totalCount)
    }

    /**
     * 评论字段是**大写短名**（`CID`/`UID`/`AID`），写错大小写不会报错、只会静默取到空值。
     */
    @Test
    fun `comment fields are uppercase short names`() {
        val payload = JmJson.decodeFromString(ForumPayload.serializer(), """
            {"total":"4","list":[{"CID":"9001","UID":"42","AID":"1478339","content":"hi",
             "nickname":"n","photo":"u.jpg","expinfo":{"level":3},"addtime":"1790825920"}]}
        """.trimIndent())
        val comment = payload.list.single()
        assertEquals("9001", comment.commentId)
        assertEquals("42", comment.uid)
        assertEquals("1478339", comment.aid)
        assertEquals(3, comment.expinfo?.level)
        assertEquals(4, payload.totalCount)
    }

    /** 无昵称时回退为「匿名」，不显示 null。 */
    @Test
    fun `comment author falls back to anonymous`() {
        val blank = JmJson.decodeFromString(CommentItem.serializer(), """{"CID":"1","nickname":""}""")
        assertEquals("匿名", blank.authorName)
    }

    /**
     * 详情：`series` 内嵌在 album 里（`chapter` 接口全项目零调用），
     * 而 `author` 是数组而不是字符串。
     */
    @Test
    fun `album embeds series and array author`() {
        val detail = JmJson.decodeFromString(AlbumDetail.serializer(), """
            {"id":1478339,"name":"x","author":["-"],"actors":["貂蝉"],"works":["真·三国无双"],
             "likes":"533","comment_total":"4","series":[{"id":"1478339","sort":"1"}]}
        """.trimIndent())
        assertEquals(listOf("-"), detail.author)
        assertEquals(533, detail.likes)
        assertEquals(4, detail.commentTotal)
        assertEquals("1478339", detail.series.single().id)
    }

    /** 逗号串的历史形态也要能吃下（`author: "a,b"`）。 */
    @Test
    fun `comma separated string becomes a list`() {
        val detail = JmJson.decodeFromString(AlbumDetail.serializer(), """{"id":"1","author":"a, b"}""")
        assertEquals(listOf("a", "b"), detail.author)
    }

    /** `total == 0` 的语义是「服务端没给」，而不是「一共 0 条」。 */
    @Test
    fun `paged list distinguishes unknown total`() {
        assertTrue(PagedList(total = 0).hasTotal.not())
        assertTrue(PagedList(total = 1).hasTotal)
    }
}
