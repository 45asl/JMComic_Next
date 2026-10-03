package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.data.BlockRules
import com.jmcomic_next.lyqs.data.remote.dto.AlbumDetail
import com.jmcomic_next.lyqs.data.remote.dto.CategoryRef
import com.jmcomic_next.lyqs.data.remote.dto.ListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 屏蔽规则的匹配语义。
 *
 * 这一层不碰网络也不碰界面，但它决定「列表里少了一部作品」是否合理 ——
 * 所以每条规则的正例与**反例**都要钉住：只测正例的话，
 * 一个「永远返回 true」的实现也能全绿。
 */
class BlockRulesTest {

    private fun item(
        name: String? = null,
        author: String? = null,
        category: String? = null,
        sub: String? = null,
    ) = ListItem(
        name = name,
        author = author,
        category = category?.let { CategoryRef(title = it) },
        categorySub = sub?.let { CategoryRef(title = it) },
    )

    @Test
    fun `empty rules hide nothing`() {
        val rules = BlockRules()
        assertTrue(rules.isEmpty)
        assertFalse(rules.hides(item(name = "任何作品", author = "任何人")))
    }

    @Test
    fun `word matches title substring ignoring case`() {
        val rules = BlockRules(words = setOf("ntr"))
        assertTrue(rules.hides(item(name = "某NTR合集")))
        assertFalse(rules.hides(item(name = "纯爱故事")))
    }

    @Test
    fun `word matches author too`() {
        // 关键词的作用域是「作品名或作者」：参考实现只匹配标题，
        // 这里刻意扩展到作者 —— 想躲开某个作者时不必去列他的每一部作品。
        val rules = BlockRules(words = setOf("某作者"))
        assertTrue(rules.hides(item(name = "无关标题", author = "某作者")))
        assertFalse(rules.hides(item(name = "无关标题", author = "别的作者")))
    }

    @Test
    fun `category matches exactly not by substring`() {
        val rules = BlockRules(categories = setOf("同人"))
        assertTrue(rules.hides(item(name = "a", category = "同人")))
        assertFalse(
            "分类要整体相等：子串匹配会把「同人」以外的相邻分类一起吞掉",
            rules.hides(item(name = "a", category = "同人志合集")),
        )
    }

    @Test
    fun `category matches sub category`() {
        val rules = BlockRules(categories = setOf("CG 图集"))
        assertTrue(rules.hides(item(name = "a", category = "单行本", sub = "cg 图集")))
        assertFalse(rules.hides(item(name = "a", category = "单行本", sub = "画师")))
    }

    @Test
    fun `missing fields do not crash or match empty rule`() {
        // 列表项字段全是可空的。空字符串与 null 都不能被任何规则命中，
        // 否则一个手滑加进去的空条目会把整站清空。
        val rules = BlockRules(words = setOf("x"), categories = setOf("y"))
        assertFalse(rules.hides(ListItem()))
        assertFalse(rules.hides(item(name = "", author = "", category = "", sub = "")))
    }

    @Test
    fun `tag hits are reported for the detail page`() {
        val rules = BlockRules(tags = setOf("纯爱", "NTR"))
        val detail = AlbumDetail(name = "作品", tags = listOf("NTR", "短篇", "纯爱"))
        assertEquals(listOf("NTR", "纯爱"), rules.hitsTags(detail))
        assertEquals(emptyList<String>(), BlockRules(tags = setOf("百合")).hitsTags(detail))
    }

    @Test
    fun `tag matching is case insensitive but returns the original label`() {
        // 详情页要把命中的标签原文回显给用户（并允许当场取消屏蔽），
        // 所以返回的必须是作品自己的写法，而不是名单里的写法。
        val rules = BlockRules(tags = setOf("ntr"))
        assertEquals(listOf("NTR"), rules.hitsTags(AlbumDetail(tags = listOf("NTR"))))
    }

    @Test
    fun `author keyword hit on detail`() {
        val rules = BlockRules(words = setOf("某作者"))
        assertTrue(rules.hitsAuthor(AlbumDetail(author = listOf("某作者", "合作者"))))
        assertFalse(rules.hitsAuthor(AlbumDetail(author = listOf("另一位"))))
    }

    @Test
    fun `tags alone do not hide list items`() {
        // 列表接口不下发标签，因此标签规则**不应该**在列表层生效。
        // 这条测试防止有人「顺手」把标签也塞进 hides：那样规则看起来生效了，
        // 实际却只有详情页能命中，排查起来非常费劲。
        val rules = BlockRules(tags = setOf("NTR"))
        assertFalse(rules.isEmpty)
        assertFalse(rules.hides(item(name = "NTR", author = "NTR")))
    }

    @Test
    fun `hit rule tags report the rules that matched`() {
        // 1.5.2：搜索页提示条要说出「是你的哪条规则挡的」，返回的必须是名单里的写法。
        val rules = BlockRules(tags = setOf("巨乳", "NTR", "百合"))
        // 「巨乳2」不该命中「巨乳」：标签规则是整体相等，不是子串（与关键词规则不同）
        assertEquals(listOf("NTR"), rules.hitRuleTags(listOf("巨乳2", "NTR")))
        assertEquals(emptyList<String>(), rules.hitRuleTags(listOf("純愛", "短篇")))
        assertEquals("一个标签都不给时不能命中", emptyList<String>(), rules.hitRuleTags(emptyList()))

        // 大小写无关，同样回显名单的写法
        assertEquals(listOf("ntr"), BlockRules(tags = setOf("ntr")).hitRuleTags(listOf("NTR")))
        // 没有任何标签规则时恒不命中
        assertEquals(emptyList<String>(), BlockRules(words = setOf("NTR")).hitRuleTags(listOf("NTR")))
    }

    @Test
    fun `matches tags agrees with hit rule tags`() {
        // 两条路径必须同源：matchesTags 曾经自己写了一遍 any/any，
        // 现在改为复用 hitRuleTags —— 这条测试防止两者语义再次分叉。
        val values = listOf("Yaoi", "短篇")
        assertTrue(BlockRules(tags = setOf("yaoi")).matchesTags(values))
        assertTrue(BlockRules(tags = setOf("yaoi")).hitRuleTags(values).isNotEmpty())
        assertFalse(BlockRules(tags = setOf("百合")).matchesTags(values))
        assertTrue(BlockRules(tags = setOf("百合")).hitRuleTags(values).isEmpty())
    }
}
