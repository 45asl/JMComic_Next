package com.jmcomic_next.lyqs.data.remote

import com.jmcomic_next.lyqs.BuildConfig
import com.jmcomic_next.lyqs.data.auth.AuthStore
import com.jmcomic_next.lyqs.data.crypto.JmCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
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
    private val authStore: AuthStore,
) {

    private val json = JmJson

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        // 放最前面：被判定为广告/追踪的请求不应产生任何流量
        .addInterceptor(AdBlockerInterceptor())
        .addInterceptor { chain ->
            // Token 与 Tokenparam 每次请求现取：会话刷新后立刻生效
            val builder = chain.request().newBuilder()
                .header("Tokenparam", session.tokenParam)
                .header("Token", session.token)
                .header("Accept", "application/json, text/plain, */*")

            // 已登录时带 JWT。每次现取，登录/登出后立即生效，无需重建客户端。
            authStore.token?.takeIf { it.isNotBlank() }?.let {
                builder.header("Authorization", "Bearer $it")
            }
            chain.proceed(builder.build())
        }
        .apply {
            if (BuildConfig.DEBUG) {
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
        return call(url, deserializer) { api.get(url, params) }
    }

    /** POST 一个业务接口并解析成 [T]。 */
    suspend fun <T> post(
        path: String,
        deserializer: DeserializationStrategy<T>,
        params: Map<String, String> = emptyMap(),
    ): T {
        val url = session.apiUrl(path)
        return call(url, deserializer) { api.post(url, params) }
    }

    private suspend fun <T> call(
        url: String,
        deserializer: DeserializationStrategy<T>,
        fetch: suspend () -> Envelope,
    ): T = withContext(Dispatchers.IO) {
        val first = runCatching { fetch() }.getOrElse { throw it.toJmException() }

        resolvePayload(first)?.let { return@withContext decode(it, deserializer) }

        // 解不开：可能是时间戳过期。刷新后原样重试一次。
        session.refresh()
        val second = runCatching { fetch() }.getOrElse { throw it.toJmException() }
        val payload = resolvePayload(second)
            ?: throw JmException("响应解密失败，可能是客户端版本过旧", JmException.Kind.Decrypt)

        if (second.code != 200) {
            throw JmException(
                second.msg?.takeIf { it.isNotBlank() } ?: "接口返回错误码 ${second.code}",
                JmException.Kind.Api,
            )
        }
        decode(payload, deserializer)
    }

    /**
     * 把 `data` 归一成 JSON 文本：密文则解密，明文则原样。
     *
     * @return 无法解密时返回 null（交给调用方决定是否重试）
     */
    private fun resolvePayload(env: Envelope): String? {
        val data = env.data ?: return null
        if (data is JsonPrimitive && data.isString) {
            return JmCrypto.decryptApiData(data.content, session.time)
        }
        return data.toString()
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
     * 这里承担一件重要的事：**服务端拒绝凭证时清掉本地会话**。
     * token 的真实有效期由服务端决定，客户端不该自己猜（官方客户端用本地 1 小时硬过期，
     * 见 [com.jmcomic_next.lyqs.data.auth.AuthStore] 的说明）。
     * 一旦收到 401/403，就说明这个 token 已经不可用，继续留着只会让后续每个请求都失败。
     */
    private fun Throwable.toJmException(): JmException {
        if (this is HttpException) {
            if (code() == 401 || code() == 403) {
                val wasLoggedIn = authStore.isLoggedIn
                authStore.clear()
                return JmException(
                    if (wasLoggedIn) "登录状态已失效，请重新登录" else "没有访问权限",
                    JmException.Kind.Auth,
                    this,
                )
            }
            return JmException("服务端返回 ${code()}", JmException.Kind.Api, this)
        }
        if (this is JmException) return this
        return JmException(
            "网络请求失败：${message ?: this::class.java.simpleName}",
            JmException.Kind.Network,
            this,
        )
    }
}
