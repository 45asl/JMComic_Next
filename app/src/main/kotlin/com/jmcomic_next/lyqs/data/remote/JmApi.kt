package com.jmcomic_next.lyqs.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.QueryMap
import retrofit2.http.Url

/**
 * 服务端统一响应外壳。
 *
 * `data` 之所以声明成 [JsonElement]，是因为它有两种形态：
 *
 *  - **字符串** —— 密文，需用会话 Token 做 AES-256-ECB 解密后再解析（绝大多数业务接口）
 *  - **对象/数组** —— 明文，直接用（少数接口以及本地调试时会遇到）
 *
 * 解析与解密的分派见 [JmRemote.resolvePayload]。
 */
@Serializable
data class Envelope(
    val code: Int = -1,
    val msg: String? = null,
    val data: JsonElement? = null,
)

/**
 * 所有请求都通过 [Url] 传入完整地址。
 *
 * 原因是 API 主机是运行时由主机发现决定的，还会在多个域名间切换；
 * 而 Retrofit 的 `baseUrl` 在构建期固定，改它就得重建整个 Retrofit 实例。
 * 用 [Url] 传全量地址可以完全绕开这个问题。
 */
interface JmApi {

    @GET
    suspend fun get(
        @Url url: String,
        @QueryMap(encoded = true) params: Map<String, String> = emptyMap(),
    ): Envelope

    /**
     * POST 一律走**表单体**（`application/x-www-form-urlencoded`）。
     *
     * 官方客户端的 `HttpUtil.fetchPost` 用 `FormData` 提交（`formData.append(...)`），
     * 参数在请求体里而不是查询串。若按查询串发送，服务端读不到 `$_POST`，
     * 表现是「接口返回 200 但业务字段全空」这种很难排查的失败。
     */
    @FormUrlEncoded
    @POST
    suspend fun post(
        @Url url: String,
        @FieldMap params: Map<String, String> = emptyMap(),
    ): Envelope
}

/** 业务层异常。带上面向用户的中文说明，避免把 OkHttp/Retrofit 的堆栈直接抛到 UI。 */
class JmException(
    override val message: String,
    val kind: Kind = Kind.Unknown,
    cause: Throwable? = null,
) : Exception(message, cause) {

    enum class Kind {
        /** 网络不可达、超时、DNS 失败 */
        Network,

        /** 拿到响应但解不开（密钥/时间戳不匹配，或服务端改了协议） */
        Decrypt,

        /** 解密成功但 code != 200 */
        Api,

        /** 解析 JSON 失败，通常是服务端字段变更 */
        Parse,

        /** 凭证缺失或已失效 */
        Auth,

        Unknown,
    }
}
