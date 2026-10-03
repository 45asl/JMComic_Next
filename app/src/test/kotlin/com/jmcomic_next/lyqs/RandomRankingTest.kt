package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.data.RandomRanking
import com.jmcomic_next.lyqs.data.remote.dto.ListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 随机结果的个性化排序（1.5.6）。
 *
 * 最容易错的两处：**标签还没到时不该乱跳**，以及**屏蔽的要整条去掉而不是排最后**。
 */
class RandomRankingTest {

    private fun comic(id: String) = ListItem(id = id, name = "作品$id")

    @Test
    fun `counts favourite tags and keeps only the most common ones`() {
        val counts = RandomRanking.favoriteTags(
            listOf(setOf("韩漫", "恋爱"), setOf("韩漫"), setOf("韩漫", "后宫")),
            limit = 2,
        )
        // 韩漫出现 3 次、恋爱 1 次、后宫 1 次；只留前 2 个，同分按名字排序保证结果稳定
        assertEquals(mapOf("韩漫" to 3, "后宫" to 1), counts)
    }

    @Test
    fun `more matching favourites come first`() {
        val favs = mapOf("韩漫" to 5, "恋爱" to 1)
        val tags = mapOf(
            "a" to setOf("韩漫"),            // 5
            "b" to setOf("韩漫", "恋爱"),     // 6
            "c" to setOf("其他"),            // 0
        )
        val ranked = RandomRanking.rank(
            items = listOf(comic("a"), comic("b"), comic("c")),
            tagsOf = { tags[it.id] },
            favoriteTags = favs,
            isBlocked = { false },
        )
        assertEquals(listOf("b", "a", "c"), ranked.map { it.id })
    }

    @Test
    fun `blocked works are removed, not merely ranked last`() {
        val ranked = RandomRanking.rank(
            items = listOf(comic("a"), comic("blocked"), comic("b")),
            tagsOf = { if (it.id == "blocked") setOf("NTR") else setOf("韩漫") },
            favoriteTags = mapOf("韩漫" to 1),
            isBlocked = { tags -> "NTR" in tags },
        )
        assertEquals(listOf("a", "b"), ranked.map { it.id })
    }

    @Test
    fun `items whose tags are not known yet keep their place`() {
        // 标签是逐条异步读到的。"还不知道"必须按 0 分稳定处理，
        // 否则每批结果一开始就会自己乱跳（用户会以为抽到了别的东西）
        val ranked = RandomRanking.rank(
            items = listOf(comic("unknown1"), comic("match"), comic("unknown2")),
            tagsOf = { if (it.id == "match") setOf("韩漫") else null },
            favoriteTags = mapOf("韩漫" to 3),
            isBlocked = { false },
        )
        assertEquals(listOf("match", "unknown1", "unknown2"), ranked.map { it.id })
        assertEquals(0, RandomRanking.score(null, mapOf("韩漫" to 3)))
        assertTrue(ranked.any { it.id == "unknown1" && it.name == "作品unknown1" })
    }
}
