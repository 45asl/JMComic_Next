package com.jmcomic_next.lyqs.data.remote

import kotlinx.serialization.json.Json

/**
 * 全局唯一的 JSON 解析配置。
 *
 * 这个 API 没有公开文档，且实测类型相当松散（同一个字段可能是数字或字符串、
 * 数组或 `{list,total}` 两种形态并存）。因此解析一律宽容：
 * 未知字段忽略、缺失字段走默认值、null 视作缺省。
 * 具体到「数字/字符串混用」这类问题由 [com.jmcomic_next.lyqs.data.remote.dto] 里的
 * Flex* 序列化器逐个字段处理。
 */
val JmJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}
