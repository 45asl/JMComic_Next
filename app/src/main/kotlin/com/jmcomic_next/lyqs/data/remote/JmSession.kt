package com.jmcomic_next.lyqs.data.remote

import com.jmcomic_next.lyqs.data.crypto.JmCrypto
import java.util.concurrent.atomic.AtomicLong

/**
 * 一次 App 进程内的会话状态：时间戳、Token、当前 API 主机与图床主机。
 *
 * **为什么时间戳要固定在会话里**：源码 `api/HttpUtil.ts` 里 `time` 是模块级常量，
 * 在模块加载时算一次（`let time = Math.floor(...)`），而它同时参与两件事：
 *
 *  1. `Token` 请求头 = `md5(time + seed)`
 *  2. **响应体 AES 密钥** = 同一个 `md5(time + seed)`
 *
 * 也就是说密钥与请求头是同一个值，且必须在解密时复用**发起请求时**的那个时间戳。
 * 如果每次请求各算一个时间戳，就会拿新密钥解旧响应，必然解出乱码。
 *
 * 固定时间戳的代价是长时间挂后台后可能被服务端判为过期，
 * 因此 [refresh] 提供显式重算，由仓储层在「解密失败」时调用一次重试。
 */
class JmSession(
    /** 客户端版本，随 `Tokenparam` 一起上报。默认与所抽取的 APK 版本一致。 */
    val clientVersion: String = DEFAULT_CLIENT_VERSION,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
) {

    private val timeSeconds = AtomicLong(nowSeconds())

    /** 当前会话时间戳（秒）。 */
    val time: Long get() = timeSeconds.get()

    /** `Token` 请求头，同时也是响应体的 AES 密钥。 */
    val token: String get() = JmCrypto.token(time)

    /** `Tokenparam` 请求头。 */
    val tokenParam: String get() = JmCrypto.tokenParam(time, clientVersion)

    /** 重算时间戳。下次取 [token] / [tokenParam] 即生效。 */
    fun refresh(): Long {
        val fresh = nowSeconds()
        timeSeconds.set(fresh)
        return fresh
    }

    /** 当前 API 主机，形如 `https://api.example.com/`（含尾斜杠）。主机发现成功后才有值。 */
    @Volatile
    var apiBaseUrl: String? = null

    /**
     * 当前主机是否「可疑」——出现了网络类失败（连接不上/超时/DNS）。
     *
     * 主机是从服务端下发的清单里**随机**挑的，挑中一个已失效的域名完全可能。
     * 若选中的主机一成不变地用到进程结束，用户遇到的就是「怎么刷新都没用」。
     * 标记之后由 [com.jmcomic_next.lyqs.data.JmRepository.bootstrap] 重新发现并换一台。
     */
    @Volatile
    var hostSuspect: Boolean = false

    /** 主机发现成功：写入新主机并清掉可疑标记。 */
    fun useHost(base: String) {
        apiBaseUrl = base
        hostSuspect = false
    }

    /** 图床主机，来自 `setting` 接口。为空时业务层应回退到 [apiBaseUrl]。 */
    @Volatile
    var imageHost: String? = null

    /** 拼出业务接口的完整 URL。 */
    fun apiUrl(path: String): String {
        val base = apiBaseUrl ?: error("API 主机尚未初始化：请先执行 JmHostDiscovery")
        return base + path.trimStart('/')
    }

    /** 拼出图片完整 URL。 */
    fun imageUrl(path: String): String {
        val base = imageHost ?: apiBaseUrl ?: error("图床主机尚未初始化")
        return base.trimEnd('/') + "/" + path.trimStart('/')
    }

    val isReady: Boolean get() = apiBaseUrl != null

    companion object {
        /** 与 `JMComic3v2.1.9.apk` 对齐，服务端会校验版本号合法性。 */
        const val DEFAULT_CLIENT_VERSION = "2.1.9"
    }
}
