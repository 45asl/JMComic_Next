package com.jmcomic_next.lyqs.data.prefs

/**
 * 屏蔽名单的三类条目（2.0.0 起从 `BlockStore` 的嵌套枚举提升为顶层类型）。
 *
 * 提升的原因：`BlockStoreApi.isBlocked` 需要它，而接口必须待在跨平台模块里。
 */
enum class BlockKind { Word, Tag, Category }
