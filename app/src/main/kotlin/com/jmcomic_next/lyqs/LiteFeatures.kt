package com.jmcomic_next.lyqs

/**
 * lite 变体的**唯一开关入口**（1.8.0）。
 *
 * 所有"lite 要裁剪什么"的判断都集中在这里，而不是把 `if (BuildConfig.LITE)` 散落到几十个地方 ——
 * 散落的开关迟早会漏一个，而漏掉的那一处会以"lite 上有个功能不能用"或"看起来还是卡"的形式出现，
 * 很难归因。
 *
 * ## 为什么用编译期常量
 *
 * [BuildConfig.LITE] 是编译期常量，所以 `if (!BuildConfig.LITE)` 包住的代码与资源会被 R8
 * **整段删掉**，不是"编译进去但不执行"。这是"最强硬手段"与"运行时开关"的分水岭：
 * 前者能让 lite 包真的变小、类真的不被加载。
 *
 * ## 时间换性能（用户明确要求的一类取舍）
 *
 * 低端设备上"多花一点时间、少占一点内存"通常是划算的，所以 lite 会：
 * - 图片**不做淡入动画**（省一次每张图的合成与重绘）；
 * - 阅读器**少预取几页**（内存换流量，而不是内存换速度）；
 * - 列表项**更早回收**（不做进出场动画）。
 */
object LiteFeatures {

    /** 是否 lite 变体。编译期常量，可被 R8 用于消除分支。 */
    const val ENABLED: Boolean = BuildConfig.LITE

    /** 阅读器预取页数：lite 更保守（默认档见阅读器的预取实现）。 */
    val prefetchPages: Int get() = if (ENABLED) 2 else DEFAULT_PREFETCH_PAGES

    /** 图片是否做淡入。lite 关掉：每张图少一次合成与重绘。 */
    val imageCrossfade: Boolean get() = !ENABLED

    /** 列表项是否做进出场动画。lite 关掉。 */
    val itemAnimations: Boolean get() = !ENABLED

    /** 是否允许壁纸与模糊（lite 一律不允许，用户要求"去掉所有壁纸和模糊"）。 */
    val wallpaperAndBlur: Boolean get() = !ENABLED

    /** 完整版的默认预取值，写在这里是为了让两边的差异一眼可见。 */
    const val DEFAULT_PREFETCH_PAGES = 3
}
