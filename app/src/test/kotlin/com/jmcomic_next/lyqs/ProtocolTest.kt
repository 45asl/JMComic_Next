package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.data.crypto.JmCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 协议推导部分的单元测试。
 *
 * 这些值的**期望值是独立算出来的** —— 用官方客户端还原出的 `Function.js` / `apiPaths.ts`
 * 里的算法，在 Node 里跑一遍得到，而不是照着 Kotlin 实现反推。
 * 否则测试只能证明「代码没变」，证明不了「代码是对的」。
 */
class ProtocolTest {

    /**
     * 切片份数 `get_num(aid, page)`。
     *
     * 这是全项目最容易「顺手优化」坏的一处：三段 aid 区间、末位字符取模、
     * 以及 `aid < 268850` 时既不取模也匹配不到任何 case（份数保持 10 —— 那是源码行为，不是遗漏）。
     */
    @Test
    fun `slice count matches the official get_num`() {
        val vectors = listOf(
            "1478339" to "00001" to 4,
            "1478339" to "00002" to 6,
            "1478339" to "00007" to 12,
            "220980" to "00001" to 10,
            "220980" to "00010" to 10,
            // 区间边界：268850 起取模 10，421926 起取模 8
            "268850" to "00001" to 6,
            "300000" to "00005" to 2,
            "421925" to "00003" to 20,
            "421926" to "00001" to 14,
            "500000" to "00009" to 14,
            "1234567" to "00042" to 8,
            // 小于 268850：不取模，落到 default 的 10
            "100000" to "01" to 10,
            "100000" to "1" to 10,
        )
        vectors.forEach { (pair, expected) ->
            val (aid, page) = pair
            assertEquals(
                "aid=$aid page=$page",
                expected,
                JmCrypto.sliceCount(aid.toInt(), page),
            )
        }
    }

    /** 份数永远是 2..20 —— 下游的 `h % num` 因此不可能除零。 */
    @Test
    fun `slice count is always a positive divisor`() {
        val pages = listOf("00001", "00002", "01", "1", "abc", "99999")
        var aid = 1
        while (aid <= 2_000_000) {
            pages.forEach { page ->
                val num = JmCrypto.sliceCount(aid, page)
                assertTrue("num=$num for aid=$aid", num in 2..20)
                assertEquals(0, num % 2)
            }
            aid += if (aid < 300_000) 7919 else 104_729
        }
    }

    /** GIF 不切；`aid < scramble_id` 的老漫画不切。 */
    @Test
    fun `unscramble is skipped for gif and old albums`() {
        assertFalse(JmCrypto.needsUnscramble("https://cdn/a/1.gif", aid = 9_999_999, scrambleId = 0))
        assertFalse(JmCrypto.needsUnscramble("https://cdn/a/1.webp", aid = 220_979, scrambleId = 220_980))
        assertTrue(JmCrypto.needsUnscramble("https://cdn/a/1.webp", aid = 220_980, scrambleId = 220_980))
        assertTrue(JmCrypto.needsUnscramble("https://cdn/a/1.WEBP", aid = 1_478_339, scrambleId = 220_980))
    }

    /** md5 与 Token 推导：期望值同样来自 Node 独立计算。 */
    @Test
    fun `md5 and token derivation`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", JmCrypto.md5(""))
        assertEquals("6ae6b037507edb82ee4699827c4b4066", JmCrypto.md5(JmCrypto.HOST_SEED))
        // 密钥就是 Token 头本身，两者必须逐字节相同
        val time = 1790908601L
        assertEquals("448b1f78d4c2d48ba4e013ffdc814aca", JmCrypto.token(time))
        assertEquals(JmCrypto.token(time), JmCrypto.md5("$time${JmCrypto.TOKEN_SEED}"))
        assertEquals("$time,2.1.9", JmCrypto.tokenParam(time, "2.1.9"))
    }
}
