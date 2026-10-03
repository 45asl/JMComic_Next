package com.jmcomic_next.lyqs.data.image

import android.graphics.Bitmap
import com.jmcomic_next.lyqs.data.crypto.JmCrypto

/**
 * 漫画图片的切片还原（Android 侧薄壳）。
 *
 * 服务端会把部分漫画的整页纵向切成若干条并**上下错位重排**，客户端拿到的是乱序版本。
 * 这里等价移植 `JMComic_SRC` 中 `utils/Function.js` 的 `onImageLoaded`：
 * 逐条把源图上的条带搬到目标图的对应位置。
 *
 * 几何关系（与源码逐字对应，变量名保持一致以便日后对账）：
 *
 *   num       = md5(aid + page) 推出的份数，见 [JmCrypto.sliceCount]
 *   remainder = h % num
 *   第 i 条：源区间 y = h - floor(h/num)*(i+1) - remainder
 *            目标起点 py = floor(h/num)*i
 *   其中 i == 0 时把 remainder 补进条高（而非目标位置）——
 *   这样所有条带拼起来正好铺满整页，不重不漏。
 */
object JmImage {

    /**
     * @param src 从服务端下载到的乱序图
     * @param page 页码字符串，必须是接口原样给出的值（参与 md5 计算）
     * @return 还原后的新图；不需要还原或参数不合法时**原样返回 [src]**
     *
     * 2.0.0 起几何计算与搬移都走 [ImageUnscramble]（跨平台模块）：
     * 这里只做「位图 → ARGB 数组 → 位图」的转换，两个平台共用同一份算法。
     */
    fun unscramble(src: Bitmap, aid: Int, page: String): Bitmap {
        val w = src.width
        val h = src.height
        if (ImageUnscramble.bands(w, h, JmCrypto.sliceCount(aid, page)).isEmpty()) return src
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = ImageUnscramble.unscramble(pixels, w, h, aid, page)
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }
}
