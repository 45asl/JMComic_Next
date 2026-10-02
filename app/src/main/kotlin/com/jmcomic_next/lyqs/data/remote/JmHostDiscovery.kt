package com.jmcomic_next.lyqs.data.remote

import com.jmcomic_next.lyqs.data.crypto.JmCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 主机清单的响应结构。
 *
 * 实测明文形如：
 * ```
 * {"Setting":["www.cdnhjk.net",...],
 *  "Server":["www.cdnhjk.net",...],
 *  "jm3_Server":[["www.cdnhjk.net","線路1"],["www.cdngwc.cc","線路2"],...]}
 * ```
 * 注意 `Server` 是**纯字符串数组**，而 `jm3_Server` 才是 `[主机, 线路名]` 对 ——
 * 两者结构不同。（源码里 `getApiHostInfo` 对 `Server` 做 `[host,label]` 解构是失效代码。）
 */
@Serializable
private data class HostPayload(
    /** 可用 API 主机列表，元素形如 `"www.example.net"`（不含 scheme）。 */
    @SerialName("Server") val servers: List<String> = emptyList(),
    /** 同样是一组主机，官方客户端在部分场景用它。 */
    @SerialName("Setting") val setting: List<String> = emptyList(),
    /** `[主机, 线路名]` 对，用于在界面上展示「线路 1/2/3…」。 */
    @SerialName("jm3_Server") val lines: List<List<String>> = emptyList(),
)

/**
 * 主机发现。
 *
 * 官方客户端的域名是构建期注入的环境变量（`REACT_APP_HOST` 等），还原出的源码里看不到字面量，
 * 因此这里改用**运行时可用**的入口：旧版 `JMComic_Next` 的 `JMConfig` 中记录的
 * `jm-discover.com/api/domain/list`。
 *
 * 响应体是密文，用固定种子 `md5(diosfjckwpqpdfjkvnqQjsik)` 做 AES-256-ECB 解密，
 * 解出 `{"Server":[...], "jm3_Server":"..."}`。对应源码 `Function.js` 的 `decryptData`
 * 与 `ApiEndpointUtil.ts` 的 `FETCH_HOST` / `setGlobalHostFromData`。
 */
object JmHostDiscovery {

    /**
     * 主机清单地址。
     *
     * 这两个 URL 来自**打包产物**：官方客户端把它们作为构建期环境变量
     * （`REACT_APP_HOST` / `REACT_APP_HOST_BACKUP`）内联进了 `main.*.js`，
     * 因此还原出的源码里看不到字面量，而反编译的 bundle 里能看到。
     * 实测两个入口都可用且返回同一份清单（托管在 BytePlus 对象存储上）。
     *
     * 注意：旧版 `JMConfig` 里的 `jm-discover.com` 已失效 —— 该域名当前**无法解析**。
     */
    private val ENDPOINTS = listOf(
        "https://rup4a04-c01.tos-ap-southeast-1.bytepluses.com/newsvr-2025.txt",
        "https://rup4a04-c02.tos-cn-hongkong.bytepluses.com/newsvr-2025.txt",
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 主机发现**专用的**客户端。
     *
     * 不复用业务客户端：那个客户端的拦截器会给请求装上 `Token` / `Authorization: Bearer <JWT>`，
     * 而这里是向第三方对象存储（BytePlus）要一份主机清单 —— 把自己的登录凭证送给一个
     * 与业务无关的第三方，只为了拿一份公开的域名列表，是不必要的暴露。
     * 这个客户端没有任何拦截器，也就不会带上任何凭证。
     */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * @return 选中的 API 主机（含尾斜杠），全部入口失败时返回 null。
     * @param pick 从候选主机里挑一个。默认随机 —— 源码用 Fisher–Yates 洗牌后取第一个，
     *   目的是把流量摊到多个域名上。测试时可注入确定性实现。
     */
    suspend fun discover(
        session: JmSession,
        pick: (List<String>) -> String? = { it.randomOrNull() },
    ): String? = withContext(Dispatchers.IO) {
        for (url in ENDPOINTS) {
            val body = runCatching {
                client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body.string()
                }
            }.getOrNull() ?: continue

            val plain = JmCrypto.decryptHostPayload(body) ?: continue
            val payload = runCatching { json.decodeFromString<HostPayload>(plain) }.getOrNull() ?: continue
            if (payload.servers.isEmpty()) continue

            val host = pick(payload.servers) ?: continue
            // 服务端给出的主机本身可能已带 scheme，两种形态都要能吃下
            val base = if (host.startsWith("http")) host.trimEnd('/') + "/" else "https://$host/"
            session.useHost(base)
            return@withContext base
        }
        return@withContext null
    }
}
