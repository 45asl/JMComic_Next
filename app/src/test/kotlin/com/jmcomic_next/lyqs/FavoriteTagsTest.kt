package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.data.FavoriteTagCache
import com.jmcomic_next.lyqs.data.FavoriteTags
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 收藏标签统计的缓存判定（1.5.6）。
 *
 * 边界写反的后果不对称：判成"永远不新鲜"只是多扫几次（慢，但结果对），
 * 判成"永远新鲜"则意味着**收藏变了也不更新**，排序会一直按旧偏好来。
 */
class FavoriteTagsTest {

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `no cache is never fresh`() {
        assertFalse(FavoriteTags.isFresh(at = 0, now = 1_000_000))
        assertFalse(FavoriteTags.isFresh(at = 0, now = 0))
    }

    @Test
    fun `fresh until the window passes`() {
        val now = 100 * day
        assertTrue(FavoriteTags.isFresh(at = now - 6 * day, now = now))
        // 刚好到期算过期：差一毫秒的边界必须钉住
        assertFalse(FavoriteTags.isFresh(at = now - 7 * day, now = now))
        assertFalse(FavoriteTags.isFresh(at = now - 8 * day, now = now))
    }

    @Test
    fun `cache round-trips through json`() {
        val raw = Json.encodeToString(
            FavoriteTagCache.serializer(),
            FavoriteTagCache(at = 123, counts = mapOf("韩漫" to 7)),
        )
        val back = FavoriteTags.decode(raw)
        assertEquals(123, back.at)
        assertEquals(mapOf("韩漫" to 7), back.counts)
    }

    @Test
    fun `bad or missing cache decodes to empty instead of throwing`() {
        // 落盘的东西可能是旧版本写的、也可能被截断 —— 读不出来就当没缓存，
        // 绝不能因为一个坏值让随机页打不开
        listOf(null, "", "{", "{\"at\":\"x\"}", "[]").forEach { raw ->
            val c = FavoriteTags.decode(raw)
            assertEquals(0, c.at)
            assertTrue(c.counts.isEmpty())
        }
    }
}
