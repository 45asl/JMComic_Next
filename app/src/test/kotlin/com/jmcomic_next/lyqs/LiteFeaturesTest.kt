package com.jmcomic_next.lyqs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * lite 变体的开关（1.8.0）。
 *
 * 这条测试的价值在于**两个变体跑同一条用例、各自断言各自的预期**：
 * 靠 `BuildConfig.FLAVOR` 让测试知道自己跑在哪里。
 *
 * 如果写成"只在 lite 下断言"（`if (LiteFeatures.ENABLED) assertFalse(...)`），
 * 那么在 full 下它会**静默通过**，等于没测；而真正危险的情况恰恰是
 * 有人在 lite 下把某个开关**接反了**（例如写成 `!LiteFeatures.ENABLED`），
 * 那种错误只有在 lite 变体下断言才会暴露。
 */
class LiteFeaturesTest {

    @Test
    fun `switches match the flavour the test is running in`() {
        when (BuildConfig.FLAVOR) {
            "lite" -> {
                assertTrue("lite 变体必须打开 LITE 常量", LiteFeatures.ENABLED)
                // 用户要求：lite 去掉所有壁纸与模糊
                assertFalse("lite 不该做壁纸与模糊", LiteFeatures.wallpaperAndBlur)
                // 用时间换性能的那批
                assertFalse("lite 不该做图片淡入", LiteFeatures.imageCrossfade)
                assertFalse("lite 不该做条目动画", LiteFeatures.itemAnimations)
                assertTrue(
                    "lite 的预取窗口必须比 full 小",
                    LiteFeatures.prefetchAfter < LiteFeatures.FULL_PREFETCH_AFTER,
                )
                assertTrue(
                    "lite 的前向预取也必须更保守",
                    LiteFeatures.prefetchBefore <= LiteFeatures.FULL_PREFETCH_BEFORE,
                )
            }

            "full" -> {
                assertFalse("full 变体不该打开 LITE 常量", LiteFeatures.ENABLED)
                assertTrue("full 保留壁纸与模糊", LiteFeatures.wallpaperAndBlur)
                assertTrue("full 保留图片淡入", LiteFeatures.imageCrossfade)
                assertTrue("full 保留条目动画", LiteFeatures.itemAnimations)
            }

            else -> fail("未知变体：${BuildConfig.FLAVOR}（新增变体时请在这里补上预期）")
        }
    }
}
