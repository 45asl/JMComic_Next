package com.jmcomic_next.lyqs.data.image

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

/**
 * 把切片还原接进 Coil 的图片管线。
 *
 * 做成 [Transformation]（而不是自己下载再还原）有两个好处：
 *  1. 结果进入 Coil 的**内存缓存**，同一页来回滚动时不会重复还原整页位图
 *  2. 还原发生在解码之后的线程池里，不占用主线程
 *
 * 缓存边界要说清楚：**磁盘缓存里存的是未还原的原始字节**（Coil 的磁盘缓存保存的是
 * 抓取到的响应体，转换结果不落盘）。所以每次冷启动后第一次看到某页，
 * 仍要重新解码 + 还原一次 —— 这不是配置漏了，而是这条管线本身的边界。
 *
 * 只有当 [com.jmcomic_next.lyqs.data.JmRepository.needsUnscramble] 判定需要还原时
 * 才应该把本转换挂到请求上。
 */
class ScrambleTransformation(
    private val aid: Int,
    private val page: String,
) : Transformation() {

    /**
     * 缓存键必须包含 [page]：同一漫画的不同页参与 md5 的字符串不同，
     * 还原方式也不同，共用键会互相污染。
     */
    override val cacheKey: String = "scramble:$aid:$page"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap =
        JmImage.unscramble(input, aid, page)
}
