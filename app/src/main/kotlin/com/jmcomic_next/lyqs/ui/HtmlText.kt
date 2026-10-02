package com.jmcomic_next.lyqs.ui

/**
 * 把服务端文本里的 HTML 变成能直接显示的纯文本。
 *
 * 这个服务端的评论正文与提示文案**带 HTML**，实测原文：
 * ```
 * 评论正文：<div style='flex-direction:row;flex-wrap:wrap;'>四姐来了</div>
 * 成功提示：评论成功发布!您已完成每日发表评论，获得「5」经验值<br>您已完成…获得「3」金币
 * ```
 * 官方是网页端，浏览器会把它们渲染成排版；本应用是 Compose 界面，直接显示就是
 * 一串标签，用户看到的是 `<div style='…'>` 这种东西。
 *
 * 处理方式：`<br>` 换行，其余标签去掉，常见实体还原，压缩连续空白。
 * 不做完整的 HTML 解析 —— 这里的目标是「别让用户看到标签」，而不是还原网页排版。
 */
internal fun String?.plainText(): String? {
    if (this.isNullOrBlank()) return null
    val text = this
        .replace(BR, "\n")
        .replace(TAG, "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
    // 去掉每行首尾空白与空行。结果为空就返回 null ——
    // 「剥完标签什么都不剩」（例如 `<div></div>` 或只有 `&nbsp;`）不该回退成原文，
    // 那会把标签又还回去；调用方拿到 null 就不会渲染这一段。
    return text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
        .takeIf { it.isNotBlank() }
}

private val BR = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
private val TAG = Regex("""<[^>]+>""")
