package com.jmcomic_next.lyqs.data

import com.jmcomic_next.lyqs.data.remote.dto.AlbumDetail
import com.jmcomic_next.lyqs.data.remote.dto.ListItem

/**
 * 屏蔽规则。
 *
 * 三份名单，都是本地数据、都由用户自己维护：
 *
 *  1. **关键词** —— 作品名或作者命中即隐藏
 *  2. **标签** —— 作品标签命中即视为屏蔽（列表接口不下发标签，因此它的生效点是详情页：
 *     打开被屏蔽标签的作品会在页面顶部提示，并可当场取消屏蔽）
 *  3. **分类** —— 分类或子分类名命中即隐藏（这是列表侧最有效的粗粒度过滤，
 *     例如「不想看同人」只需屏蔽「同人」）
 *
 * 匹配一律是**大小写无关的子串匹配**：用户想屏蔽「NTR」时不该还要考虑 ntr/Ntr 的区别。
 *
 * 设计参考了 [haka_comic](https://github.com/raoxwup/haka_comic)（GPL-3.0）的屏蔽功能
 * —— 它按「标签命中 / 分类黑名单 / 标题含关键词」三条规则过滤列表。
 * 本项目是 Kotlin/Compose，与它的 Flutter/Dart 实现没有共用代码（详见 README 的致谢）。
 */
data class BlockRules(
    val words: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
) {
    /** 没有任何规则时，列表过滤整条路径都可以跳掉。 */
    val isEmpty: Boolean get() = words.isEmpty() && tags.isEmpty() && categories.isEmpty()

    /**
     * 这个列表项是否该被隐藏。
     *
     * 只用到列表项真正带的字段：`name` / `author` / `category` / `category_sub`。
     * 标签不在这里（接口不给），见 [hitsTags]。
     */
    fun hides(item: ListItem): Boolean {
        if (isEmpty) return false
        val name = item.name.orEmpty()
        val author = item.author.orEmpty()
        if (words.any { name.contains(it, ignoreCase = true) || author.contains(it, ignoreCase = true) }) {
            return true
        }
        val category = item.category?.title.orEmpty()
        val sub = item.categorySub?.title.orEmpty()
        return categories.any { candidate ->
            category.equals(candidate, ignoreCase = true) || sub.equals(candidate, ignoreCase = true)
        }
    }

    /**
     * 详情页用：该作品的标签里有哪些命中屏蔽名单。
     *
     * 返回命中的标签本身，界面据此提示「含已屏蔽标签：xxx」并给出取消入口 ——
     * 比整页遮住更有用：用户可能只是想确认一下再决定看不看。
     */
    fun hitsTags(detail: AlbumDetail): List<String> = hitsTags(detail.tags)

    /** 同上，但直接吃一组标签字符串 —— 列表标签屏蔽拿不到 `AlbumDetail`，只有标签集合。 */
    fun hitsTags(values: Collection<String>): List<String> =
        values.filter { tag -> tags.any { it.equals(tag, ignoreCase = true) } }

    /** 这组标签是否命中任一标签规则。 */
    fun matchesTags(values: Collection<String>): Boolean =
        tags.isNotEmpty() && values.any { v -> tags.any { it.equals(v, ignoreCase = true) } }

    /** 详情页用：作者是否命中关键词（命中则给出「已屏蔽该作者」的提示）。 */
    fun hitsAuthor(detail: AlbumDetail): Boolean =
        detail.author.any { author -> words.any { author.contains(it, ignoreCase = true) } }
}
