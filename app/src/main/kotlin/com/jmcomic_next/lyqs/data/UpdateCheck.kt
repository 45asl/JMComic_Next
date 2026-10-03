package com.jmcomic_next.lyqs.data

/**
 * 检查更新（1.8.0）。
 *
 * 数据来自 GitHub Releases（`releases/latest` 的 `tag_name`），比较逻辑是**纯函数**，
 * 因为这里有一个不显眼但一定会踩的坑：
 *
 * **本应用的 versionName 带变体后缀**（`1.8.0.lite` / `1.8.0.debug` / `1.8.0.litedebug`），
 * 而 GitHub 的 tag 是干净的（`v1.8.0`）。直接字符串比较永远得出"有新版本"，
 * 直接按数字段比较又会因为多出来的段而判断错。
 *
 * 所以先**归一化**：去掉 `v` 前缀、只取前三段数字、忽略其余后缀。
 */
object UpdateCheck {

    /**
     * 把版本串归一化成可比较的数字段；解析不出来返回 null。
     *
     * 例：`v1.8.0` → `[1, 8, 0]`；`1.8.0.lite` → `[1, 8, 0]`；`1.8.0.litedebug` → `[1, 8, 0]`。
     */
    fun parts(raw: String?): List<Int>? {
        val cleaned = raw?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
        if (cleaned.isEmpty()) return null
        val numbers = cleaned.split('.', '-', '+')
            .takeWhile { it.isNotEmpty() && it.all(Char::isDigit) }
            .mapNotNull { it.toIntOrNull() }
        return numbers.takeIf { it.isNotEmpty() }
    }

    /**
     * 远端版本是否比当前版本新。
     *
     * 逐段比较，缺的段按 0 处理（`1.8` 与 `1.8.0` 视为相同）；任一侧解析不出来时返回 false ——
     * **宁可不说"有更新"**：误报会让用户去装一个并不存在的新版本，比漏报更糟。
     */
    fun isNewer(latest: String?, current: String?): Boolean {
        val remote = parts(latest) ?: return false
        val local = parts(current) ?: return false
        val size = maxOf(remote.size, local.size)
        for (i in 0 until size) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    /** 该把用户带到哪个页面：优先 Release 页，其次是仓库页。 */
    fun releaseUrl(tagOrUrl: String?, repo: String = REPO): String {
        val t = tagOrUrl?.trim().orEmpty()
        if (t.startsWith("http")) return t
        return if (t.isEmpty()) "https://github.com/$repo/releases" else "https://github.com/$repo/releases/tag/$t"
    }

    const val REPO = "moyingyilang/JMComic_Next"

    /** GitHub 的"最新发布"接口。 */
    const val LATEST_RELEASE_API = "https://api.github.com/repos/$REPO/releases/latest"
}
