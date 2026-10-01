package com.jmcomic_next.lyqs

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.jmcomic_next.lyqs.data.JmRepository

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

    /** 全局唯一的仓储实例：内部持有会话（API 主机、Token、图床主机）。 */
    val repository: JmRepository by lazy { JmRepository.create() }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .build()

    companion object {
        lateinit var instance: JmApp
            private set
    }
}
