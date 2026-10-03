package com.jmcomic_next.lyqs.data.prefs

import com.jmcomic_next.lyqs.data.BlockRules
import kotlinx.coroutines.flow.StateFlow

/**
 * 屏蔽名单存储接口（2.0.0 起）。
 *
 * 成员按**仓储层与界面的实际用量**定：仓储只读 [snapshot]，
 * 界面要观察 [state] 并增删词/标签/分区。
 *
 * `isBlocked` 依赖 [BlockKind]，它已从 `BlockStore` 的嵌套枚举提升为顶层类型。
 */
interface BlockStoreApi {
    val state: StateFlow<BlockRules>

    fun snapshot(): BlockRules

    fun addWord(word: String)
    fun removeWord(word: String)
    fun addTag(tag: String)
    fun removeTag(tag: String)
    fun addCategory(name: String)
    fun removeCategory(name: String)

    /** 某条内容是否命中名单（界面用来提示「已经在名单里」）。 */
    fun isBlocked(type: BlockKind, value: String): Boolean
}
