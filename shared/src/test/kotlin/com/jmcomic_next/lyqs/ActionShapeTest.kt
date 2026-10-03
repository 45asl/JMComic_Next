package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.data.toActionResult
import com.jmcomic_next.lyqs.data.trackedOrFalse
import com.jmcomic_next.lyqs.data.remote.JmJson
import com.jmcomic_next.lyqs.data.remote.dto.ActionResult
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 动作类接口的两种响应形态。
 *
 * 这一组测试来自一次**真机上的失败**：`album_sertracking` 的 `data` 不是对象，
 * 而是一句话（实测 `"已追踪!"` / `"已取消追踪!"`）。当时的表现是
 * 「点一下追更，状态闪一下就回到原样」—— 因为解密的代码只认 JSON，
 * 把它当解密失败，触发重试，把非幂等的 POST 又发了一遍。
 */
class ActionShapeTest {

    private fun json(text: String): JsonElement = JmJson.decodeFromString(JsonElement.serializer(), text)

    /** 对象形态：服务端给 {status, type, msg} 时照常解析。 */
    @Test
    fun `object payload keeps status and type`() {
        val action = json("""{"status":"ok","type":"add","msg":"收藏成功"}""").toActionResult()
        assertTrue(action.isOk)
        assertEquals("add", action.type)
        assertEquals("收藏成功", action.msg)
    }

    /** 一整句话也算成功：那句话本来就是给用户看的提示。 */
    @Test
    fun `plain message payload is treated as success`() {
        val tracked = json("\"已追踪!\"").toActionResult()
        assertTrue(tracked.isOk)
        assertEquals("已追踪!", tracked.msg)

        val untracked = json("\"已取消追踪!\"").toActionResult()
        assertTrue(untracked.isOk)
        assertEquals("已取消追踪!", untracked.msg)
    }

    /** 空 data 不能算成功。 */
    @Test
    fun `null payload is not success`() {
        val action = (null as JsonElement?).toActionResult()
        assertFalse(action.isOk)
    }

    /**
     * 追更状态的判读：只有明确的真值才算真。
     * 把失败响应当成「已追更」比反过来糟 —— 用户会以为自己早就关注了。
     */
    @Test
    fun `tracking state interpretation`() {
        assertTrue("已追踪!".trackedOrFalse())
        assertFalse("已取消追踪!".trackedOrFalse())
        assertTrue("true".trackedOrFalse())
        assertTrue("1".trackedOrFalse())
        assertFalse("false".trackedOrFalse())
        assertFalse("0".trackedOrFalse())
        // 未登录/失败这类文案不能被当成已追更
        assertFalse("請先登入會員".trackedOrFalse())
        assertFalse("".trackedOrFalse())
    }
}
