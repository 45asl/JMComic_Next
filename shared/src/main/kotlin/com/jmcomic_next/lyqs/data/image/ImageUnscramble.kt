package com.jmcomic_next.lyqs.data.image

import com.jmcomic_next.lyqs.data.crypto.JmCrypto

/**
 * 漫画图片切片还原的**算法本体**（2.0.0 起放进跨平台模块）。
 *
 * 与 Android 的 Bitmap 无关：输入输出都是 ARGB 像素数组，平台侧只负责
 * 「把位图读成数组、把数组写回位图」。这样 Android 与桌面用的是**同一份几何计算** ——
 * 这种逐条搬移的算法如果两边各写一遍，必然出现一边错位、另一边正常的情况，而且极难发现。
 *
 * 几何关系（与 JMComic_SRC 的 utils/Function.js 逐字对应，变量名保持一致以便对账）：
 *
 *   num       = md5(aid + page) 推出的份数，见 [JmCrypto.sliceCount]
 *   remainder = h % num
 *   第 i 条：源区间 y = h - floor(h/num)*(i+1) - remainder
 *            目标起点 py = floor(h/num)*i
 *   其中 i == 0 时把 remainder 补进条高（而非目标位置），这样所有条带拼起来正好铺满整页。
 */
object ImageUnscramble {

    /** 一条带：源图起点、目标图起点、高度（单位都是像素行）。 */
    data class Band(val srcY: Int, val dstY: Int, val height: Int)

    /**
     * 计算还原所需的条带；返回空列表表示**不需要还原**（份数 <= 1 或参数不合法）。
     *
     * 把几何单独抽出来是为了能直接对它写测试 —— 不必构造位图，用纯数字就能验证
     * 「条带不重不漏地铺满整页」这条最容易出错的性质。
     */
    fun bands(width: Int, height: Int, num: Int): List<Band> {
        if (num <= 1 || height < num || width <= 0 || height <= 0) return emptyList()
        val base = height / num
        val remainder = height % num
        val out = ArrayList<Band>(num)
        for (i in 0 until num) {
            var copyH = base
            var py = base * i
            val y = height - base * (i + 1) - remainder
            if (i == 0) copyH += remainder else py += remainder
            if (copyH <= 0) continue
            val srcTop = y.coerceAtLeast(0)
            val srcBottom = (y + copyH).coerceAtMost(height)
            var usable = srcBottom - srcTop
            if (usable <= 0) continue
            val room = height - py
            if (usable > room) usable = room
            if (usable <= 0) continue
            out += Band(srcTop, py, usable)
        }
        return out
    }

    /**
     * 还原像素；不需要还原时**原样返回传入的数组**。
     *
     * @param pixels 长度必须为 width * height 的 ARGB 数组
     */
    fun unscramble(pixels: IntArray, width: Int, height: Int, aid: Int, page: String): IntArray {
        require(pixels.size >= width * height) { "像素数组长度不足" }
        val bands = bands(width, height, JmCrypto.sliceCount(aid, page))
        if (bands.isEmpty()) return pixels
        val out = IntArray(pixels.size)
        for (b in bands) {
            System.arraycopy(pixels, b.srcY * width, out, b.dstY * width, b.height * width)
        }
        return out
    }
}
