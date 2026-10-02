package com.jmcomic_next.lyqs.data.wallpaper

import android.content.Context
import androidx.core.content.edit
import com.jmcomic_next.lyqs.data.remote.JmJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把 Bing 返回地址里的 `_宽x高` 段改成我们要的尺寸。
 *
 * Bing 的图按尺寸段裁剪，实测改段是**真的换图**（请求 `_1080x1920.jpg` 拿回来就是
 * 1080×1920，不是把横图硬拉长）。这一点很值得做：手机是竖屏，如果直接用返回的
 * `_1920x1080` 横图，竖屏铺满时会被裁掉两侧很大一块，构图经常就废了。
 *
 * 只在能匹配 `_数字x数字` 时改写，其余原样返回 —— 这是别人的地址，猜错了就得整张图加载失败。
 */
internal fun bingSizedUrl(url: String, portrait: Boolean): String {
    val target = if (portrait) "1080x1920" else "1920x1080"
    // 只改**第一段**尺寸。`Regex.replace` 会把所有匹配都改掉，于是查询串里另一个
    // 碰巧长得像尺寸的段也会被一起改写 —— 那不是我们要动的地址，改坏了整张图就加载不出来。
    // 这里用 find + 手工拼接而不是 replaceFirst：后者只收 String 替换式（`$3` 这种），
    // 拼接能把「保留原扩展名、其余原样不动」写得更直白。
    val m = BING_SIZE.find(url) ?: return url
    return url.substring(0, m.range.first) +
        "_${target}${m.groupValues[3]}" +
        url.substring(m.range.last + 1)
}

/** Bing 的尺寸段形如 `_1920x1080.jpg`，只认这一种形状。 */
private val BING_SIZE = Regex("_(\\d+)x(\\d+)(\\.jpg)", RegexOption.IGNORE_CASE)

/**
 * 壁纸来源。
 *
 * 前四种对应博客 `src/config/wallpapers.ts` 里的模式（博客是
 * 全部随机 / Bing 专属 / 二次元全部随机 / 二次元自选 / 纯渐变），
 * 这里去掉了「二次元自选」的多选框（那需要把几百条图源清单打包进来），
 * 换成一个 [Custom] 让用户自己填地址。
 *
 * **默认不是 Bing 而是 [Off]**，这一点与博客不同：博客是网页，背景图不影响阅读；
 * 本应用是阅读器，默认应该没有任何第三方请求，想要背景图的人自己去开。
 */
enum class WallpaperMode(val label: String, val desc: String) {
    Off("纯渐变", "不加载任何图片，只用内置渐变底（不发任何请求）"),
    Bing("Bing 每日", "Bing 每日壁纸，来源 bing.biturl.top"),
    Anime("二次元随机", "t.alcy.cc / t.mwm.moe / loliapi 三个源依次尝试"),
    All("全部随机", "Bing 与二次元混合"),
    Custom("自定义地址", "自己填一个图片直链"),
}

/**
 * 壁纸状态。整份状态都在内存里以 [StateFlow] 暴露，改一项就落一次盘。
 *
 * [url] 是**当前正在显示的那张**；[credit] 是它的署名（Bing 会回摄影者/地点），
 * 界面上要显示出来 —— 用了别人的图，署名是最低要求。
 */
data class WallpaperState(
    val mode: WallpaperMode = WallpaperMode.Off,
    val url: String? = null,
    val credit: String? = null,
    /** 图片高斯模糊 0..24（对应博客的 `--wallpaper-blur`，同样是 0–24）。 */
    val blur: Int = 0,
    /** 压暗遮罩 0..0.6（对应博客的 `--wallpaper-dim`，默认 0.12）。 */
    val dim: Float = 0.12f,
    /** 自动更换间隔（分钟），0 = 只在手动「换一张」时换。 */
    val intervalMinutes: Int = 0,
    val customUrl: String = "",
    val loading: Boolean = false,
    val error: String? = null,
) {
    /** 该不该画图片层。纯渐变模式下永远是 false。 */
    val showsImage: Boolean get() = mode != WallpaperMode.Off && !url.isNullOrBlank()
}

/**
 * 壁纸来源与已取到的图片地址。
 *
 * **对第三方接口要克制。** 博客里写得很清楚：静态站无法阻止别人抄走接口地址，
 * 能做的是不让自己成为放大器。这里照同样的做法：
 *
 *  - 已经取到的地址**缓存在本地轮换**（攒到 [CACHE_MIN] 张之后每轮不再请求），
 *  - Bing 的地址每天只重新取一次（保留「每日壁纸」的意义），
 *  - 手动连点有冷却，不会把接口当图床刷。
 *
 * 另外这些请求走的是**应用自己的 OkHttp 客户端**（与业务请求同一个），
 * 所以广告域名拦截同样覆盖它；壁纸不是广告，不会被拦，但如果哪天某个图源
 * 变成广告域名，它会被自动挡掉。
 */
class WallpaperStore(context: Context, private val client: OkHttpClient) {

    private val appContext = context.applicationContext

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<WallpaperState> = _state.asStateFlow()

    /** 更新与「换一张」可能并发（用户连点），串行化避免把接口打两遍。 */
    private val gate = Mutex()
    private var lastFetchAt = 0L

    /** 刚取回的那张图的署名，随地址一起落盘。 */
    private var fetchedCredit: String? = null

    val snapshot: WallpaperState get() = _state.value

    fun setMode(mode: WallpaperMode) {
        update { it.copy(mode = mode, error = null) }
    }

    fun setBlur(value: Int) = update { it.copy(blur = value.coerceIn(0, MAX_BLUR)) }

    fun setDim(value: Float) = update { it.copy(dim = value.coerceIn(0f, MAX_DIM)) }

    fun setInterval(minutes: Int) = update { it.copy(intervalMinutes = minutes.coerceAtLeast(0)) }

    fun setCustomUrl(url: String) = update { it.copy(customUrl = url.trim()) }

    /**
     * 换一张。
     *
     * @param force 手动「换一张」时为 true：忽略冷却与「今天已取过」的限制
     *   （但缓存里还有没用过的地址时仍然优先用缓存，不额外打接口）。
     */
    suspend fun next(force: Boolean = false) = gate.withLock {
        val s = _state.value
        when (s.mode) {
            WallpaperMode.Off -> update { it.copy(url = null, credit = null, error = null) }

            WallpaperMode.Custom -> {
                if (s.customUrl.isBlank()) {
                    update { it.copy(url = null, credit = null, error = "还没有填地址") }
                } else {
                    update { it.copy(url = s.customUrl, credit = null, error = null) }
                }
            }

            else -> {
                val now = System.currentTimeMillis()
                if (!force && now - lastFetchAt < COOLDOWN_MS) return@withLock
                lastFetchAt = now
                update { it.copy(loading = true, error = null) }
                val next = runCatching { pick(s, force) }
                update { prev ->
                    next.fold(
                        onSuccess = { it.copy(loading = false) },
                        onFailure = { prev.copy(loading = false, error = it.message ?: "取图失败") },
                    )
                }
            }
        }
    }

    /** 从缓存里取下一张；缓存不够或跨天了才联网补一张。 */
    private suspend fun pick(s: WallpaperState, force: Boolean): WallpaperState {
        val wantBing = s.mode == WallpaperMode.Bing ||
            (s.mode == WallpaperMode.All && rotationTick() % 2 == 0)
        val cache = readCache(if (wantBing) KEY_BING else KEY_ANIME)
        val day = today()
        val staleBing = wantBing && prefs.getString(KEY_BING_DAY, null) != day

        // 什么时候才联网：缓存没攒够（4 张）或空的，或者 Bing 跨天了。
        // 攒够之后就一直吃缓存轮换 —— 第三方接口不该被我们反复调用
        val needFetch = cache.isEmpty() || cache.size < CACHE_MIN || (wantBing && staleBing)
        if (needFetch) {
            val fetched = if (wantBing) fetchBing() else fetchAnime()
            val merged = (cache + fetched).distinct().take(CACHE_MAX)
            writeCache(if (wantBing) KEY_BING else KEY_ANIME, merged)
            if (wantBing) {
                prefs.edit {
                    putString(KEY_BING_DAY, day)
                    putString(KEY_CREDIT, fetchedCredit)
                }
            }
        }

        val pool = readCache(if (wantBing) KEY_BING else KEY_ANIME)
            .ifEmpty { cache }
            .ifEmpty { throw IllegalStateException("图源没有返回可用地址") }

        // 轮换：按游标取，跳过当前这张，避免连点两次看到同一张
        var cursor = prefs.getInt(KEY_CURSOR, 0) % pool.size
        if (pool.size > 1 && pool.getOrNull(cursor) == s.url) cursor = (cursor + 1) % pool.size
        prefs.edit { putInt(KEY_CURSOR, (cursor + 1) % pool.size) }

        return s.copy(
            url = pool[cursor],
            credit = if (wantBing) prefs.getString(KEY_CREDIT, null) else ANIME_CREDIT,
            error = null,
        )
    }

    private suspend fun fetchBing(): List<String> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(BING_API).header("Accept", "application/json").build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("Bing 接口 HTTP ${resp.code}")
            val parsed = JmJson.decodeFromString(BingResponse.serializer(), body)
            fetchedCredit = parsed.copyright
            val portrait = appContext.resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_PORTRAIT
            listOfNotNull(
                parsed.url?.takeIf { it.isNotBlank() }
                    ?.let { bingSizedUrl(it, portrait = portrait) },
            )
        }
    }

    /**
     * 二次元源。
     *
     * 这些地址**直接返回图片字节**，所以「可用性」只能靠真发一次请求判断；
     * 成功时记下**重定向之后的最终地址**，之后直接用它，省掉每次绘制的跳转。
     * 三个源依次尝试，全挂才算失败。
     */
    private suspend fun fetchAnime(): List<String> = withContext(Dispatchers.IO) {
        val failures = mutableListOf<String>()
        for (api in ANIME_APIS) {
            val got = runCatching {
                val req = Request.Builder().url(api).header("Accept", "image/*").build()
                client.newCall(req).execute().use { resp ->
                    val finalUrl = resp.request.url.toString()
                    if (resp.isSuccessful) {
                        // 读一小段就够判断可用性，不必把整张图拉下来（Coil 之后会自己取）
                        resp.body?.byteStream()?.read(ByteArray(512))
                        finalUrl
                    } else {
                        throw IllegalStateException("HTTP ${resp.code}")
                    }
                }
            }
            got.onSuccess { return@withContext listOf(it) }
            got.onFailure { failures += "$api: ${it.message}" }
        }
        throw IllegalStateException("二次元图源都不可用（${failures.joinToString("；")}）")
    }

    /** 混合模式下的轮换计数：用游标本身做奇偶，避免再存一个计数器。 */
    private fun rotationTick(): Int = prefs.getInt(KEY_CURSOR, 0)

    private fun update(transform: (WallpaperState) -> WallpaperState) {
        val next = transform(_state.value)
        write(next)
        _state.value = next
    }

    private fun read(): WallpaperState = WallpaperState(
        mode = WallpaperMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) }
            ?: WallpaperMode.Off,
        url = prefs.getString(KEY_URL, null),
        credit = prefs.getString(KEY_CREDIT, null),
        blur = prefs.getInt(KEY_BLUR, 0),
        dim = prefs.getFloat(KEY_DIM, 0.12f),
        intervalMinutes = prefs.getInt(KEY_INTERVAL, 0),
        customUrl = prefs.getString(KEY_CUSTOM, "").orEmpty(),
    )

    private fun write(s: WallpaperState) = prefs.edit {
        putString(KEY_MODE, s.mode.name)
        putString(KEY_URL, s.url)
        putString(KEY_CUSTOM, s.customUrl)
        putInt(KEY_BLUR, s.blur)
        putFloat(KEY_DIM, s.dim)
        putInt(KEY_INTERVAL, s.intervalMinutes)
    }

    private fun readCache(key: String): List<String> = runCatching {
        JmJson.decodeFromString(
            ListSerializer(String.serializer()),
            prefs.getString(key, null) ?: "[]",
        )
    }.getOrDefault(emptyList())

    private fun writeCache(key: String, urls: List<String>) = prefs.edit {
        putString(key, JmJson.encodeToString(ListSerializer(String.serializer()), urls))
    }

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    @Serializable
    private data class BingResponse(val url: String? = null, val copyright: String? = null)

    companion object {
        const val MAX_BLUR = 24
        const val MAX_DIM = 0.6f
        /** 手动换图的冷却：连点不该把接口当图床刷。 */
        const val COOLDOWN_MS = 800L

        private const val CACHE_MIN = 4
        private const val CACHE_MAX = 8
        private const val BING_API =
            "https://bing.biturl.top/?resolution=1920&format=json&index=random&mkt=zh-CN"
        private val ANIME_APIS = listOf(
            "https://t.alcy.cc/ycy",
            "https://t.mwm.moe/pc",
            "https://www.loliapi.com/acg/",
        )
        private const val ANIME_CREDIT = "二次元图源"

        private const val PREFS_NAME = "jm_wallpaper"
        private const val KEY_MODE = "mode"
        private const val KEY_URL = "url"
        private const val KEY_CREDIT = "credit"
        private const val KEY_CUSTOM = "custom_url"
        private const val KEY_BLUR = "blur"
        private const val KEY_DIM = "dim"
        private const val KEY_INTERVAL = "interval"
        private const val KEY_CURSOR = "cursor"
        private const val KEY_BING = "cache_bing"
        private const val KEY_ANIME = "cache_anime"
        private const val KEY_BING_DAY = "bing_day"
    }
}
