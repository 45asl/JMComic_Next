package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.data.UpdateCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 检查更新的版本比较（1.8.0）。
 *
 * 重点是**变体后缀**：本应用的 versionName 是 `1.8.0.lite` 这类，而 GitHub tag 是 `v1.8.0`。
 * 这里最容易犯的错是拿两个字符串直接比 —— 那样永远显示"有新版本"。
 */
class UpdateCheckTest {

    @Test
    fun `variant suffixes are ignored on both sides`() {
        assertEquals(listOf(1, 8, 0), UpdateCheck.parts("1.8.0.lite"))
        assertEquals(listOf(1, 8, 0), UpdateCheck.parts("1.8.0.litedebug"))
        assertEquals(listOf(1, 8, 0), UpdateCheck.parts("1.8.0.debug"))
        assertEquals(listOf(1, 8, 0), UpdateCheck.parts("v1.8.0"))
        // 关键：带后缀的当前版本与干净 tag 相等时，不该报"有更新"
        assertFalse(UpdateCheck.isNewer("v1.8.0", "1.8.0.lite"))
        assertFalse(UpdateCheck.isNewer("v1.8.0", "1.8.0.debug"))
    }

    @Test
    fun `newer versions are detected segment by segment`() {
        assertTrue(UpdateCheck.isNewer("v1.8.1", "1.8.0.lite"))
        assertTrue(UpdateCheck.isNewer("v1.9.0", "1.8.9"))
        assertTrue(UpdateCheck.isNewer("v2.0.0", "1.99.99"))
        // 位数不同也要对：1.8 与 1.8.0 视为相同；1.10 比 1.9 新（不是字符串比较！）
        assertFalse(UpdateCheck.isNewer("v1.8", "1.8.0"))
        assertTrue(UpdateCheck.isNewer("v1.10.0", "1.9.0"))
    }

    @Test
    fun `older or unparseable versions never claim an update`() {
        assertFalse(UpdateCheck.isNewer("v1.7.0", "1.8.0"))
        // 拿不到 tag、或 tag 是别的东西时不报更新：误报会让用户去装一个不存在的版本
        assertFalse(UpdateCheck.isNewer(null, "1.8.0"))
        assertFalse(UpdateCheck.isNewer("", "1.8.0"))
        assertFalse(UpdateCheck.isNewer("latest", "1.8.0"))
        assertFalse(UpdateCheck.isNewer("v1.8.0", null))
    }

    @Test
    fun `release url prefers a full url and falls back to the repo`() {
        assertEquals("https://example.com/x", UpdateCheck.releaseUrl("https://example.com/x"))
        assertEquals(
            "https://github.com/moyingyilang/JMComic_Next/releases/tag/v1.8.0",
            UpdateCheck.releaseUrl("v1.8.0"),
        )
        assertEquals(
            "https://github.com/moyingyilang/JMComic_Next/releases",
            UpdateCheck.releaseUrl(null),
        )
    }
}
