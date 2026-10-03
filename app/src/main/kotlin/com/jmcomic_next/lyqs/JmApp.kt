package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.data.auth.AndroidKeystoreKeyProvider
import com.jmcomic_next.lyqs.data.auth.SecureStore
import com.jmcomic_next.lyqs.data.prefs.SharedPrefsKeyValueStore
import com.jmcomic_next.lyqs.BuildConfig
import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.TagCache
import com.jmcomic_next.lyqs.data.TagBlockResolver
import com.jmcomic_next.lyqs.data.auth.AuthStore
import com.jmcomic_next.lyqs.data.prefs.BlockStore
import com.jmcomic_next.lyqs.data.wallpaper.WallpaperStore
import com.jmcomic_next.lyqs.data.prefs.ReadProgressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

/**
 * 应用级依赖容器。
 *
 * 这个规模的应用不值得引入 DI 框架 —— 一个懒加载的仓储实例，
 * 通过 CompositionLocal 提供给 Compose 树即可，依赖关系一眼可见。
 *
 * 同时它是 Coil 的全局 [SingletonImageLoader.Factory]：Coil 3 把网络能力拆成了
 * 独立构件，不加 [OkHttpNetworkFetcherFactory] 就只会加载本地资源，
 * 网络图会静默失败（表现为永远显示占位图）。
 */
class JmApp : Application(), SingletonImageLoader.Factory {

    /** 账号会话：JWT 与会员信息，经 Keystore 加密落盘。 */
    val authStore: AuthStore by lazy { AuthStore(SecureStore(SharedPrefsKeyValueStore(this, "jm_secure"), AndroidKeystoreKeyProvider())) }

    /** 阅读进度（作品 → 上次读到哪一话）。服务端历史只有作品粒度，这一层必须在本地。 */
    val readProgress: ReadProgressStore by lazy { ReadProgressStore(SharedPrefsKeyValueStore(this, "jm_read_progress")) }

    /** 屏蔽规则（关键词 / 分类 / 标签）。 */
    val blockStore: BlockStore by lazy { BlockStore(SharedPrefsKeyValueStore(this, "jm_block")) }

    /**
     * 壁纸来源与已取到的地址。
     *
     * 复用业务请求的 OkHttp 客户端，于是广告域名拦截同样覆盖壁纸流量；
     * 它懒加载，用户没开壁纸时不会有任何请求。
     */
    val wallpaperStore: WallpaperStore by lazy { WallpaperStore(this, repository.okHttp) }

    /**
     * 列表里的**标签屏蔽**（1.5.1）。
     *
     * 列表接口不返回标签，标签只在详情接口里，所以判定是**异步**的：条目在列表里可见时
     * 请求一次，结果落进 [TagCache] 并落盘，命中的 id 由 [TagBlockResolver.hidden] 报出。
     *
     * 三条与代价有关的取舍：
     *  · 并发 3 —— 它是「顺手补一层屏蔽」，不该和用户正在看的列表抢带宽；
     *  · 缓存落盘 —— 否则每滚一次列表就把同一批作品的详情重取一遍；
     *  · **规则跟随 [BlockStore]** —— 用户加了标签规则要立刻生效，且只用缓存重算、不重新请求。
     * 没有标签规则时它一个请求都不会发（resolver 内部首行就 return）。
     */
    val tagBlocker: TagBlockResolver by lazy {
        val prefs = getSharedPreferences(PREFS_TAG_CACHE, MODE_PRIVATE)
        TagBlockResolver(
            fetchTags = { id -> runCatching { repository.album(id).tags.toSet() }.getOrNull() },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            maxParallel = 3,
            cache = TagCache().apply { load(prefs.getString(KEY_TAG_CACHE, null)) },
            onPersist = { text -> prefs.edit().putString(KEY_TAG_CACHE, text).apply() },
        ).also { resolver ->
            resolver.setRules(blockStore.snapshot())
            // 规则变了要重算命中集合（只用缓存，不发请求）
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                blockStore.state.collect { resolver.setRules(it) }
            }
        }
    }

    /** 全局唯一的仓储实例：持有接口主机、请求 Token、图床主机与账号会话。 */
    val repository: JmRepository by lazy {
        JmRepository.create(authStore = authStore, blockStore = blockStore,
            debug = BuildConfig.DEBUG,
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                // 复用业务请求的客户端：这样图片通道同样受 AdBlocker 保护，
                // 也共享连接池与超时设置。若在此 new 一个默认客户端，
                // 广告拦截器就只覆盖了 API 流量，图片通道是敞开的。
                add(OkHttpNetworkFetcherFactory(callFactory = { repository.okHttp }))
            }
            // 取图与解码**分开**给并行度。
            //
            // Coil 3 把这件事拆成了按阶段的协程上下文（fetcher / decoder / interceptor），
            // 默认值是偏保守的；漫画是「几十张大图排队」，并行度直接决定翻页跟不跟得上。
            // 两者的性格不一样，所以取值也不一样：
            //  - 取图是 IO 密集、不吃 CPU，可以开得宽（核数×2，上限 16）；
            //  - 解码吃 CPU 和堆内存，开太多只会在低端机上互相抢内存，按核数封顶 8。
            .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(fetchParallelism))
            .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(decodeParallelism))
            // lite 关掉淡入：每张图少一次合成与重绘，低端设备上这是"用观感换流畅"的直接一笔
            .crossfade(LiteFeatures.imageCrossfade)
            // 内存缓存提到 25%：阅读时来回滚动、切上一话/下一话都会重看同一批图，
            // 默认档在长图流里很快就会被挤掉，然后变成「明明刚看过却要重新下载」。
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .build()

    companion object {
        lateinit var instance: JmApp
            private set

        /** 标签屏蔽缓存的落盘位置（与规则本身分开存：一个是用户输入，一个是网络回来的事实）。 */
        private const val PREFS_TAG_CACHE = "jm_tag_cache"
        private const val KEY_TAG_CACHE = "tag_cache_v1"

        private val cores: Int = Runtime.getRuntime().availableProcessors()

        /** 取图并行度：IO 密集，可以宽一些。 */
        private val fetchParallelism: Int = (cores * 2).coerceIn(4, 16)

        /** 解码并行度：吃 CPU 与堆内存，按核数封顶，避免低端机上互相抢内存。 */
        private val decodeParallelism: Int = cores.coerceIn(2, 8)
    }
}
