package com.jmcomic_next.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * 桌面端的网络图片加载（2.0.0 第一版）。
 *
 * 为什么先手写而不是直接引 Coil：这里只需要「下载字节 → 解码 → 交给 Compose」三件事，
 * 用 Skiko（Compose Desktop 自带的依赖）与 JDK 就能做到，不增加额外依赖。
 * 等界面铺开、需要磁盘缓存与取消策略时再换 Coil 更划算。
 *
 * 缓存只有内存一层：同一次会话里重复出现的封面不会重复下载；
 * 退出即失效。磁盘缓存属于后面的活。
 */
object RemoteImage {

    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    /** 同步取（已缓存时立即返回）；未缓存返回 null。 */
    fun cached(url: String): ImageBitmap? = cache[url]

    /** 下载并解码；失败返回 null（界面按"没有封面"处理，不弹错）。 */
    suspend fun load(url: String): ImageBitmap? {
        cache[url]?.let { return it }
        val bytes = withContext(Dispatchers.IO) {
            runCatching {
                val conn = URI(url).toURL().openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                // 部分图床对缺少 UA 的请求直接拒绝
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (jmcomic-next-desktop)")
                conn.inputStream.use { it.readBytes() }
            }.getOrNull()
        } ?: return null
        val bmp = runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull() ?: return null
        cache[url] = bmp
        return bmp
    }
}

/**
 * 在 Composable 里按 URL 取图：先给缓存，再异步下载。
 *
 * 返回值是 `null` 表示"还没有/拿不到"，调用方据此显示占位块。
 */
@Composable
fun rememberRemoteImage(url: String?): ImageBitmap? {
    if (url.isNullOrBlank()) return null
    var bitmap by remember(url) { mutableStateOf(RemoteImage.cached(url)) }
    LaunchedEffect(url) {
        if (bitmap == null) bitmap = RemoteImage.load(url)
    }
    return bitmap
}
