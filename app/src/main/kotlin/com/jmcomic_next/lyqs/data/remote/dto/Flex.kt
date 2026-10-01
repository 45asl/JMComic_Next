package com.jmcomic_next.lyqs.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * 宽松标量序列化器。
 *
 * 存在的理由：这个 API 的类型不稳定 —— 同一个 `id` 在不同接口可能回 `123` 或 `"123"`，
 * 布尔位可能回 `true` / `1` / `"1"`。如果用严格的 String/Int/Boolean 声明，
 * 服务端一次无预告的类型微调就会让整个页面解析失败；而这些差异对业务语义没有影响。
 *
 * 统一策略：**能在不丢信息的前提下转换就转换，转不了就退到安全默认值**，
 * 不抛异常。真正缺失的关键字段由业务层判断，而不是在这里炸掉。
 */
object FlexString : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val json = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val el = json.decodeJsonElement()) {
            is JsonNull -> ""
            is JsonPrimitive -> el.content
            else -> el.toString()
        }
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

object FlexStringOrNull : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexStringOrNull", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val el = json.decodeJsonElement()) {
            is JsonNull -> null
            is JsonPrimitive -> el.content.takeIf { it.isNotEmpty() && it != "null" }
            else -> el.toString()
        }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

object FlexInt : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val json = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val content = when (val el = json.decodeJsonElement()) {
            is JsonNull -> return 0
            is JsonPrimitive -> el.content
            else -> return 0
        }
        return content.toIntOrNull() ?: content.toDoubleOrNull()?.toInt() ?: 0
    }

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

object FlexBool : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexBool", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        val json = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        val content = when (val el = json.decodeJsonElement()) {
            is JsonNull -> return false
            is JsonPrimitive -> el.content
            else -> return false
        }
        return content.lowercase() in setOf("1", "true", "yes", "y")
    }

    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}

/**
 * `List<String>`，同时接受两种形态：
 *  - `["a","b"]`
 *  - `"a,b"`（逗号分隔的字符串 —— 标签/作者/作品这些字段常有这种写法）
 */
object FlexStringList : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexStringList", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): List<String> {
        val json = decoder as? JsonDecoder ?: return emptyList()
        return when (val el = json.decodeJsonElement()) {
            is JsonNull -> emptyList()
            is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf(String::isNotBlank) }
            is JsonPrimitive -> el.content
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
            else -> emptyList()
        }
    }

    override fun serialize(encoder: Encoder, value: List<String>) {
        encoder.encodeString(value.joinToString(","))
    }
}
