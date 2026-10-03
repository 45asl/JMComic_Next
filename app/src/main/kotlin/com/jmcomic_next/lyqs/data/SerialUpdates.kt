package com.jmcomic_next.lyqs.data

import com.jmcomic_next.lyqs.data.remote.dto.ListItem

/**
 * 「我追的连载里，哪些更新了」。
 *
 * ## 为什么比时间戳，而不是比"最新话 vs 我读到第几话"
 *
 * 章节目录只在**详情接口**里（`AlbumDetail.series`）。按话数比较意味着给每部追的作品
 * 各发一次详情请求 —— 那正是标签屏蔽已经背着的负担，不该再叠一层。
 *
 * 而连载列表项自带 `update_at`（本来就是服务端给的，以前只被拿去当封面 URL 的版本号），
 * 本地又记着"最后读它的时间"：**一比即可，零额外请求、零后台服务**。
 *
 * 代价要说清楚：这样只能判断"**我读过之后它又更新过**"，判断不了"更新的是不是我没看的那一话"。
 * 对"提醒你回去看看"这个用途足够，且不会误报 —— 时间戳只会在真的更新时前进。
 *
 * 判定为"追的"的前提是**读过**（本地有时间记录）：没读过的作品出现在连载列表里
 * 不算"更新"，否则整个列表都会亮起来，提示就失去意义了。
 */
object SerialUpdates {

    /**
     * 把 `update_at` 解析成 epoch 毫秒。
     *
     * 服务端给的是字符串形式的数字，**单位不保证**：实测是秒（10 位），
     * 但类型定义里它是 number，未来换成毫秒（13 位）也不奇怪。
     * 用一个保守的分界（1e11）区分：小于它的当秒，大于等于的当毫秒。
     * 解析不出来返回 null → 该条不参与判定（宁可漏报，不可误报）。
     */
    fun parseEpochMillis(raw: String?): Long? {
        val v = raw?.trim()?.toLongOrNull() ?: return null
        if (v <= 0) return null
        return if (v < 100_000_000_000L) v * 1000 else v
    }

    /**
     * 从连载列表里挑出"读过、且在那之后服务端又更新过"的作品。
     *
     * @param lastReadAt 给定作品 id，返回"最后读它的时间"（毫秒）；没读过返回 null。
     */
    /**
     * 这批更新的**指纹**：给"同一批只通知一次"用。
     *
     * 排序后再拼，所以顺序变化不会造成重复通知；空集合返回空串。
     * 抽成纯函数是为了能被单测直接钉住 —— 它埋在依赖 Context 的检查流程里时测不到。
     */
    fun fingerprint(items: List<ListItem>): String =
        items.map { it.id }.sorted().joinToString(",")

    fun updated(
        items: List<ListItem>,
        lastReadAt: (String) -> Long?,
    ): List<ListItem> = items.filter { item ->
        val updatedAt = parseEpochMillis(item.updateAt) ?: return@filter false
        val readAt = lastReadAt(item.id) ?: return@filter false   // 没读过 = 没追，不算更新
        updatedAt > readAt
    }
}
