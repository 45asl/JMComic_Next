package com.jmcomic_next.lyqs.data

import com.jmcomic_next.lyqs.data.remote.dto.ListItem

/**
 * 随机结果的**个性化排序**（1.5.6）。
 *
 * 用户的要求是：按收藏里出现最多的标签，把命中的往前放、匹配少的往后放，
 * 而命中屏蔽标签的**整条去掉**（"飞起来"）。
 *
 * ## 为什么单独抽出来
 *
 * 标签拿不到现成的：列表接口**不下发标签**，所以每个候选都要额外读一次详情（1.5.1 的屏蔽功能
 * 也是因为这个才"逐条读详情"）。也就是说这套排序**必然伴随异步**：
 * 先按原顺序显示，标签陆续到了再重排。
 *
 * 把排序抽成纯函数，就可以对这一堆边界（标签还没到、全部被屏蔽、分数相同）单独钉测试，
 * 而不必依赖网络。
 */
object RandomRanking {

    /**
     * 统计收藏里各标签出现的次数，只保留最多的 [limit] 个。
     *
     * 限制数量的理由：收藏里长尾标签很多，留着会稀释权重 —— 一本冷门作品带一个只出现一次的标签，
     * 不该和"出现 30 次的标签"等价。而且列表越小、比较越省事。
     */
    fun favoriteTags(tagsPerWork: List<Set<String>>, limit: Int = 40): Map<String, Int> =
        tagsPerWork
            .flatMap { it }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .associate { it.key to it.value }

    /**
     * 按"命中收藏标签的权重之和"重排，并去掉命中屏蔽规则的。
     *
     * - [tagsOf] 返回 `null` 表示**这一条的标签还没读到**：按 0 分处理，
     *   于是它留在原地（`sortedByDescending` 是稳定排序），而不是被甩到最末尾 ——
     *   把"还不知道"和"确定不匹配"混为一谈，会让每批结果一开始就乱跳。
     * - [isBlocked] 由调用方传入（就是现有那套屏蔽规则），命中即整条移除。
     */
    fun rank(
        items: List<ListItem>,
        tagsOf: (ListItem) -> Set<String>?,
        favoriteTags: Map<String, Int>,
        isBlocked: (Set<String>) -> Boolean,
    ): List<ListItem> = items
        .filterNot { item -> tagsOf(item)?.let(isBlocked) == true }
        .sortedByDescending { item -> score(tagsOf(item), favoriteTags) }

    /** 一条作品命中收藏标签的权重之和。标签未知（null）时为 0。 */
    fun score(tags: Set<String>?, favoriteTags: Map<String, Int>): Int =
        tags?.sumOf { favoriteTags[it] ?: 0 } ?: 0
}
