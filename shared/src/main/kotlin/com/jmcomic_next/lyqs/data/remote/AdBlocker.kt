package com.jmcomic_next.lyqs.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * 广告与追踪域名拦截。
 *
 * 先说清楚这套应用里广告的**实际来源**，否则这个类看起来像在防一个不存在的问题。
 * 对官方客户端（JMComic3 v2.1.9）审计后确认：广告**全部**来自两个接口，且都是客户端
 * 主动去拉的 ——
 *
 *  - `ad_content_all`：按 `adKey` 取素材，官方代码里有 60 多个插槽
 *    （`app_home_top`、`app_home_float`、`app_chapter_top`、`app_thewayhome`、
 *    `app_detail_introduction_bottom_jm3`、`download1~3` 等，覆盖首页/详情/阅读/影片/小说）
 *  - `advertise_all`：启动时的四封面全屏广告
 *
 * 广告素材的数据形态是 `{adv_id, adv_img, adv_link, adv_name, adv_text, adv_title, adv_recommend}`。
 *
 * **本应用从不调用这两个接口，也没有实现任何 adKey 插槽**，因此默认就是无广告的。
 * 实测也确认服务端不会把广告混进业务载荷：`promote` / `latest` / `search` / `album` /
 * `comic_read` 五个响应里都搜不到任何 `adv_` / `advertise` / `ad_content` 特征，
 * 阅读流的图片也全部落在站点自己的 CDN 上，没有第三方域名夹带。
 *
 * 那这个类防的是什么？**防回归**。上面两条结论都是「当前如此」，而这个客户端会长期
 * 跟着服务端演进走。有了这层拦截，即使将来某处引入了第三方广告/追踪地址，
 * 请求也会在发出去之前被挡掉，而不是悄悄把用户行为发出去。
 *
 * 判定用**后缀匹配**，因此 `ads.example.com` 与 `example.com` 都会被覆盖。
 * 只拦第三方广告与追踪网络；站点自身的 API 与图床域名一律放行。
 */
object AdBlocker {

    /**
     * 第三方广告 / 追踪 / 归因域名。
     *
     * 覆盖三类：广告交易与投放网络、行为分析、安装归因。
     * 值得单独点出的是 `clarity.ms` —— 官方 Web 端在 `index.html` 里**直接内联**了
     * Microsoft Clarity 的埋点脚本（站点 ID 可在 `index.html` 中看到），
     * 本应用不做同类采集，这里也一并在网络层挡掉。
     */
    private val BLOCKED_SUFFIXES = setOf(
        // 广告交易与投放
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "amazon-adsystem.com",
        "adnxs.com",
        "adsrvr.org",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "smartadserver.com",
        "smaato.net",
        "mopub.com",
        "inmobi.com",
        "adcolony.com",
        "chartboost.com",
        "tapjoy.com",
        "supersonicads.com",
        "vungle.com",
        "ironsrc.com",
        "fyber.com",
        "mintegral.com",
        "startapp.com",
        "mobvista.com",
        "pangolin-sdk-toutiao.com",
        "pangle.io",
        "unityads.unity3d.com",
        "applovin.com",
        // 行为分析与热图
        "google-analytics.com",
        "googletagmanager.com",
        "clarity.ms",
        "scorecardresearch.com",
        "quantserve.com",
        "moatads.com",
        "umeng.com",
        "umengcloud.com",
        "cnzz.com",
        // 安装归因
        "adjust.com",
        "appsflyer.com",
        "branch.io",
        "kochava.com",
        "tenjin.io",
    )

    /**
     * 该主机是否应被拦截。
     *
     * 抽成纯函数是为了可单独验证：它的正确性不该依赖一次真实的网络请求才能确认。
     */
    fun isBlocked(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (h.isEmpty()) return false
        return BLOCKED_SUFFIXES.any { suffix -> h == suffix || h.endsWith(".$suffix") }
    }

    /** 被拦截的主机数量，供设置页展示「已屏蔽 N 类广告域名」。 */
    val blockedDomainCount: Int get() = BLOCKED_SUFFIXES.size
}

/**
 * 把 [AdBlocker] 接进 OkHttp。
 *
 * 拦截时返回一个合成的 403 空响应，而不是抛异常 —— 抛异常会让调用方（尤其是图片加载）
 * 把「被拦截」和「网络故障」混为一谈，日志里也难区分。
 * 合成响应会让上层自然走到「加载失败 → 占位图」的既有分支。
 */
class AdBlockerInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (AdBlocker.isBlocked(request.url.host)) {
            return Response.Builder()
                .request(request)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(403)
                .message("Blocked by AdBlocker: ${request.url.host}")
                .body("".toResponseBody(null))
                .build()
        }
        return chain.proceed(request)
    }
}
