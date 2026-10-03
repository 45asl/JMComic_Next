package com.jmcomic_next.lyqs.data.prefs

import com.jmcomic_next.lyqs.data.BlockRules
import kotlinx.coroutines.flow.StateFlow

/**
 * 屏蔽名单存储接口（2.0.0 起）。
 *
 * 成员按**仓储层与界面的实际用量**定：仓储只读 [snapshot]，
 * 界面要观察 [state] 并增删词/标签/分区。
 *
 * 注意这里暂时**没有** `isBlocked`：它依赖 `Kind`，而 `Kind` 目前是 Android 侧
 * `BlockStore` 的嵌套枚举。等 `Kind` 移到跨平台模块后再补进这个接口。
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
}
