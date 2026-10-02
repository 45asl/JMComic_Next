package com.jmcomic_next.lyqs.ui.screens.reader

import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.image.ScrambleTransformation
import com.jmcomic_next.lyqs.data.remote.dto.ReadImage

/**
 * 已加载页面的实测宽高比（宽 ÷ 高）记忆。
 *
 * **它解决的是一处真实的手感问题。** 服务端返回的图片列表里**没有尺寸** ——
 * [ReadImage] 只有 `image` 与 `page`，所以加载中的占位只能猜一个比例。
 * 猜错了会怎样：图加载完成的那一刻条目高度变化，整条列表跟着跳一下，
 * 用户正在读的位置被「弹」走。伪长图流里这个跳动尤其难受，因为它正好发生在滚动过程中。
 *
 * 记忆分两层：
 *  1. **按 URL** —— 同一页再看一次时绝对准（切话回来、来回滚）。
 *  2. **按作品** —— 同一部作品的页面尺寸基本一致（同一批图源、同一次切图），
 *     所以第一页实测出来之后，后面**还没加载**的页就能直接用这个比例占位，
 *     把「每一页都可能跳一次」压成「最多第一页跳一次」。
 */
internal object PageRatioMemory {

    /** 可信的宽高比范围。超出这个区间说明拿到的不是真实解码尺寸（占位/未就绪）。 */
    private val VALID = 0.1f..2.5f

    private const val MAX_URLS = 600
    private const val MAX_ALBUMS = 16

    private val byUrl = object : LinkedHashMap<String, Float>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>) =
            size > MAX_URLS
    }

    private val byAlbum = object : LinkedHashMap<Int, Float>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Float>) =
            size > MAX_ALBUMS
    }

    /** 这一页上次实测到的比例；没量过返回 null。 */
    fun ratioFor(url: String): Float? = synchronized(byUrl) { byUrl[url] }

    /** 这部作品最近一次实测到的比例，用来给还没加载的页占位。 */
    fun albumRatio(aid: Int): Float? = synchronized(byAlbum) { byAlbum[aid] }

    /** 记录一次实测。脏值直接丢掉，不让它污染之后的占位。 */
    fun remember(aid: Int, url: String, ratio: Float) {
        if (!ratio.isFinite() || ratio !in VALID) return
        synchronized(byUrl) { byUrl[url] = ratio }
        synchronized(byAlbum) { byAlbum[aid] = ratio }
    }
}

/**
 * 阅读页图片的**统一**请求构造。
 *
 * 抽出来不是为了让代码好看，而是因为**预取与显示必须用完全一样的请求**：
 * Coil 的缓存键由请求本身决定，预取时若少一个转换（比如反切片），
 * 缓存里存下的就是另一份东西，用户滚到时仍然要重新下载一遍 —— 预取等于白做。
 * 把构造收在一处，两边就不可能走偏。
 */
internal fun readerImageRequest(
    context: PlatformContext,
    image: ReadImage,
    aid: Int,
    scrambleId: Int,
    repo: JmRepository,
    attempt: Int = 0,
): ImageRequest = ImageRequest.Builder(context)
    .data(image.image)
    .crossfade(true)
    .apply {
        // 重试时要换一个 memoryCacheKey，否则 Coil 判定「请求没变化」不会真的重发，
        // 界面会永远停在失败状态上
        if (attempt > 0) memoryCacheKey("${image.image}#retry$attempt")
        if (repo.needsUnscramble(image.image, aid, scrambleId)) {
            transformations(ScrambleTransformation(aid = aid, page = image.fileNameStem))
        }
    }
    .build()
