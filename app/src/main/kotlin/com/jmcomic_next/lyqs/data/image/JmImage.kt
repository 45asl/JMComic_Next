package com.jmcomic_next.lyqs.data.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.jmcomic_next.lyqs.data.crypto.JmCrypto

/**
 * 漫画图片的切片还原。
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
     */
    fun unscramble(src: Bitmap, aid: Int, page: String): Bitmap {
        val num = JmCrypto.sliceCount(aid, page)
        val w = src.width
        val h = src.height
        if (num <= 1 || h < num || w <= 0) return src

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val remainder = h % num

        for (i in 0 until num) {
            var copyH = h / num
            var py = copyH * i
            val y = h - copyH * (i + 1) - remainder
            if (i == 0) {
                copyH += remainder
            } else {
                py += remainder
            }
            if (copyH <= 0) continue

            // drawImage 对越界源矩形是宽容的（按可用区域绘制），这里显式裁剪以免抛异常
            val srcTop = y.coerceAtLeast(0)
            val srcBottom = (y + copyH).coerceAtMost(h)
            val usable = srcBottom - srcTop
            if (usable <= 0) continue

            canvas.drawBitmap(
                src,
                Rect(0, srcTop, w, srcBottom),
                Rect(0, py, w, py + usable),
                paint,
            )
        }
        return out
    }
}
