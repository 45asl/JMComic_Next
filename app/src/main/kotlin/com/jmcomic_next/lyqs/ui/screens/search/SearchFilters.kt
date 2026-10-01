package com.jmcomic_next.lyqs.ui.screens.search

/**
 * 搜索筛选条件。
 *
 * 取值全部对照官方客户端的定义（`JsonData.ts` 的 `SearchSortData` / `SearchTypeData`），
 * 不自行发明参数：
 *
 *  - `o`           排序。`""` 最新、`mv` 最多点阅、`mp` 最多图片、`tf` 最多爱心、`old` 最旧
 *  - `search_type` 检索字段。`site` 站内、`work` 作品、`author` 作者、`tag` 标签、`character` 登场人物
 *  - `y` / `m`     年份与月份，都是字符串，留空表示不限
 *
 * 「最旧」这一档比较特殊：官方除了把它作为 `o` 传给服务端，**还会在本地按 `adddate` 重排**，
 * 因此这里保留了 [isLocalOldest] 标记，由 ViewModel 决定是否做二次排序。
 */
data class SearchFilters(
    val order: String = Order.LATEST.key,
    val type: String = Type.SITE.key,
    val year: String = "",
    val month: String = "",
) {
    val isLocalOldest: Boolean get() = order == Order.OLDEST.key

    /** 是否处于非默认状态，用于在界面上提示「已筛选」。 */
    val isDefault: Boolean
        get() = order == Order.LATEST.key && type == Type.SITE.key &&
            year.isEmpty() && month.isEmpty()

    /** 排序选项。 */
    enum class Order(val key: String, val label: String) {
        LATEST("", "最新"),
        MOST_VIEWS("mv", "最多点阅"),
        MOST_IMAGES("mp", "最多图片"),
        MOST_HEARTS("tf", "最多爱心"),
        OLDEST("old", "最旧"),
    }

    /** 检索字段。文案取自官方内联的 i18n 值（原文为繁体，此处统一为简体以与界面一致）。 */
    enum class Type(val key: String, val label: String) {
        SITE("site", "站内搜索"),
        WORK("work", "作品"),
        AUTHOR("author", "作者"),
        TAG("tag", "标签"),
        CHARACTER("character", "登场人物"),
    }
}
