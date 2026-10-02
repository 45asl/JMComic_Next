package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.ui.plainText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 服务端文本里的 HTML 要变成纯文本。
 *
 * 用例都是**实测原文**：评论正文被包在 `<div style=…>` 里，成功提示里带 `<br>`。
 * 不处理的话，用户看到的就是一串标签（在真机上确实看到了，才回来加的这条）。
 */
class PlainTextTest {

    @Test
    fun `comment body loses its wrapper div`() {
        assertEquals(
            "四姐来了",
            "<div style='flex-direction:row;flex-wrap:wrap;'>四姐来了</div>".plainText(),
        )
    }

    @Test
    fun `br becomes a line break`() {
        assertEquals(
            "评论成功发布!您已完成每日发表评论，获得「5」经验值\n您已完成每日发表评论，获得「3」金币",
            "评论成功发布!您已完成每日发表评论，获得「5」经验值<br>您已完成每日发表评论，获得「3」金币".plainText(),
        )
        assertEquals("上\n下", "上<br/>下".plainText())
        assertEquals("上\n下", "上<BR />下".plainText())
    }

    @Test
    fun `entities are restored`() {
        assertEquals("a & b < c > d", "a &amp; b &lt; c &gt; d".plainText())
        assertEquals("他说\"你好\"", "他说&quot;你好&quot;".plainText())
    }

    @Test
    fun `blank and null stay null`() {
        assertNull(null.plainText())
        assertNull("".plainText())
        assertNull("   ".plainText())
        assertNull("<div></div>".plainText())
    }

    @Test
    fun `plain text passes through unchanged`() {
        assertEquals("就是一句普通的话", "就是一句普通的话".plainText())
    }
}
