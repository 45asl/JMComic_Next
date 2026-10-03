package com.jmcomic_next.lyqs.data.remote

import com.jmcomic_next.lyqs.data.auth.AuthSession
import com.jmcomic_next.lyqs.data.crypto.JmCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 业务接口的统一入口：发请求 → 解密 `data` → 反序列化。
 *
 * 三步里最容易出错的是第二步。协议要求**用发起请求时的时间戳**参与 AES 密钥
 * （见 [JmSession] 的说明），因此这里在解密失败时会刷新会话时间戳并**重试一次** ——
 * 覆盖「App 长时间挂后台导致时间戳过期」这一种可自愈的失败。
 * 仍失败则抛 [JmException.Kind.Decrypt]，那种情况通常是协议变了，重试没有意义。
 */
class JmRemote(
    val session: JmSession,
    private val authStore: AuthSession,
    /** 是否打印请求日志。Android 传 BuildConfig.DEBUG，桌面端传自己的判断 —— 跨平台模块里没有 BuildConfig。 */
    private val debug: Boolean = false,
) {

    private val json = JmJson

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        // 放最前面：被判定为广告/追踪的请求不应产生任何流量
        .addInterceptor(AdBlockerInterceptor())
        .addInterceptor { chain ->
            // 时间戳**取一次**：Token 头、Tokenparam 头与响应解密用的密钥必须来自同一个值。
            // 分两次 `session.time` 取，中间若发生 refresh（并发请求的解密重试会触发），
            // 就会出现「头用的是旧时间戳、解密用新密钥」这种必然解出乱码的组合。
            val time = session.time
            val request = chain.request().newBuilder()
                .header("Tokenparam", JmCrypto.tokenParam(time, session.clientVersion))
                .header("Token", JmCrypto.token(time))
                .header("Accept", "application/json, text/plain, */*")
                // 把这次请求用的时间戳钉在请求上，响应回来时按键解密 —— 见 resolvePayload
                .tag(RequestStamp::class.java, RequestStamp(time))
                .apply {
                    // 已登录时带 JWT。每次现取，登录/登出后立即生效，无需重建客户端。
                    authStore.token?.takeIf { it.isNotBlank() }?.let {
                        header("Authorization", "Bearer $it")
                    }
                }
                .build()
            chain.proceed(request)
        }
        .apply {
            if (debug) {
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
                )
            }
        }
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val api: JmApi = Retrofit.Builder()
        // 每个方法都通过 @Url 传完整地址，这里的 baseUrl 只是 Retrofit 的形式要求
        .baseUrl("https://localhost/")
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(JmApi::class.java)

    /** GET 一个业务接口并解析成 [T]。 */
    suspend fun <T> get(
        path: String,
        deserializer: DeserializationStrategy<T>,
        params: Map<String, String> = emptyMap(),
    ): T {
        val url = session.apiUrl(path)
        // GET 可以安全重发：解不开多半是时间戳过期
        return call(url, deserializer, retryable = true) { api.get(url, params) }
    }

    /**
     * GET 一个接口并把**解密后的原文**交给调用方。
     *
     * 给形态不确定的接口用：实测有些接口的 data 是一句人话（"已追踪!"），
     * 硬按某个 DTO 反序列化会抛解析错，而那句人话本身就是要显示给用户的内容。
     */
    suspend fun getRaw(path: String, params: Map<String, String> = emptyMap()): String =
        get(path, JsonElement.serializer(), params).let { el ->
            (el as? JsonPrimitive)?.content ?: el.toString()
        }

    /** POST 一个业务接口并解析成 [T]。 */
    suspend fun <T> post(
        path: String,
        deserializer: DeserializationStrategy<T>,
        params: Map<String, String> = emptyMap(),
    ): T {
        val url = session.apiUrl(path)
        // POST **不重发**：这个服务端有大量「点一下改一次状态」的接口（追更、收藏、
        // 发表评论…），重发一次就是把用户刚做的操作撤销或重复。解不开就报错，
        // 让用户自己决定要不要再点。
        return call(url, deserializer, retryable = false) { api.post(url, params) }
    }

    private suspend fun <T> call(
        url: String,
        deserializer: DeserializationStrategy<T>,
        retryable: Boolean,
        fetch: suspend () -> Response<Envelope>,
    ): T = withContext(Dispatchers.IO) {
        val first = runCatching { fetch() }.getOrElse { throw it.toJmException() }
        val envelope = first.envelopeOrThrow()
        // 业务码非 200、或服务端给了 errorMsg（实测 HTTP 200 + code 200 + errorMsg 也会出现，
        // 例如路径不合法），都先按错误处理 —— 否则会拿一个空数组去反序列化目标类型
        if (envelope.code != 200 || (envelope.errorMsg?.isNotBlank() == true)) {
            throw apiFailure(envelope)
        }

        resolvePayload(envelope, first)?.let { return@withContext decode(it, deserializer) }

        if (!retryable) {
            throw JmException("响应解密失败，且该请求不可重发（写操作）", JmException.Kind.Decrypt)
        }

        // 解不开：可能是时间戳过期（服务端按时间戳校验密钥）。换一个时间戳原样重试一次。
        session.refresh()
        val second = runCatching { fetch() }.getOrElse { throw it.toJmException() }
        val retryEnvelope = second.envelopeOrThrow()
        if (retryEnvelope.code != 200 || (retryEnvelope.errorMsg?.isNotBlank() == true)) {
            throw apiFailure(retryEnvelope)
        }
        val payload = resolvePayload(retryEnvelope, second)
            ?: throw JmException("响应解密失败，可能是客户端版本过旧", JmException.Kind.Decrypt)
        decode(payload, deserializer)
    }

    /**
     * 校验 HTTP 状态，并给出**服务端自己的**说明。
     *
     * 顺序很关键：**先看状态码，再尝试解密**。
     * 这个服务端把失败响应写成 `{"code":401,"data":[],"errorMsg":"…"}`（HTTP 401），
     * `data` 是空的明文数组、根本没有密文可解。若先解密再判断，
     * 每次「密码错误」都会退化成「解密失败 → 刷新时间戳 → 再试一次 → 仍报解密失败」：
     * 用户看到的是协议问题，还白跑一次请求。
     */
    private fun Response<Envelope>.envelopeOrThrow(): Envelope {
        val parsed = body()
        if (!isSuccessful) {
            // 服务端的说明在 `errorMsg` 里，而它只存在于错误响应体上
            val raw = runCatching { errorBody()?.string() }.getOrNull()
            val fromRaw = raw?.let {
                runCatching { json.decodeFromString(Envelope.serializer(), it).message }.getOrNull()
            }
            throw httpFailure(code(), parsed?.message ?: fromRaw)
        }
        return parsed ?: throw JmException("服务端返回了空响应体", JmException.Kind.Parse)
    }

    /**
     * 把 `data` 归一成 JSON 文本：密文则解密，明文则原样。
     *
     * 解密的密钥必须来自**这次请求实际发出去的那个时间戳**（拦截器把它钉在请求 tag 上），
     * 而不是「此刻的时间戳」—— 并发请求时后者可能已被另一次失败的解密重试
     * （[JmSession.refresh]）换成新值，用它去解旧请求的响应必然乱码。
     *
     * @return 无法解密时返回 null（交给调用方决定是否重试）
     */
    private fun resolvePayload(env: Envelope, response: Response<Envelope>): String? {
        val data = env.data ?: return null
        if (data is JsonPrimitive && data.isString) {
            val time = response.raw().request.tag(RequestStamp::class.java)?.time ?: session.time
            return JmCrypto.decryptApiData(data.content, time)
        }
        return data.toString()
    }

    /** 业务码非 200 或服务端明确给了 `errorMsg` 时的统一处理。 */
    private fun apiFailure(env: Envelope): JmException = JmException(
        env.message ?: "接口返回错误码 ${env.code}",
        JmException.Kind.Api,
    )

    /**
     * HTTP 非 2xx 的统一处理。
     *
     * **只有 401 才当作「凭证失效」**：403 也可能是中间设备/WAF 拒绝，
     * 据此清掉用户的登录态会让一次无关的拒绝变成一次强制重新登录。
     * 已登录用户被顶掉时用自己的文案（并读服务端的说明兜底），未登录时直接用服务端的，
     * 例如登录失败会原样显示「无效的用户名和/或密码!」而不是「没有访问权限」。
     */
    private fun httpFailure(status: Int, serverMessage: String?): JmException {
        if (status == 401) {
            val wasLoggedIn = authStore.isLoggedIn
            authStore.clear()
            val message = when {
                wasLoggedIn -> "登录状态已失效，请重新登录"
                serverMessage != null -> serverMessage
                else -> "没有访问权限"
            }
            return JmException(message, JmException.Kind.Auth)
        }
        return JmException(serverMessage ?: "服务端返回 $status", JmException.Kind.Api)
    }

    private fun <T> decode(payload: String, deserializer: DeserializationStrategy<T>): T =
        try {
            json.decodeFromString(deserializer, payload)
        } catch (t: Throwable) {
            throw JmException("响应解析失败：${t.message}", JmException.Kind.Parse, t)
        }

    /**
     * 把底层异常翻译成带用户可读文案的 [JmException]。
     *
     * 网络类失败会把当前 API 主机标记为「可疑」，下一次 [com.jmcomic_next.lyqs.data.JmRepository.bootstrap]
     * 就会重新做主机发现 —— 否则一旦随机挑中的那个域名不可用，
     * 整个进程生命周期内所有请求都会打在这个死主机上，用户只能杀进程重来。
     */
    private fun Throwable.toJmException(): JmException {
        if (this is JmException) return this
        if (this is HttpException) {
            val status = code()
            val parsed = runCatching {
                json.decodeFromString(Envelope.serializer(), response()?.errorBody()?.string().orEmpty())
            }.getOrNull()
            return httpFailure(status, parsed?.message)
        }
        // 走到这里基本都是连接/超时/DNS：当前主机的可用性存疑
        session.hostSuspect = true
        return JmException(
            "网络请求失败：${message ?: this::class.java.simpleName}",
            JmException.Kind.Network,
            this,
        )
    }
}

/** 钉在 OkHttp 请求上的「本次请求使用的时间戳」，用于响应解密。 */
internal data class RequestStamp(val time: Long)
