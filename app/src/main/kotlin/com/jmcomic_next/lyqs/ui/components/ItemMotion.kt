package com.jmcomic_next.lyqs.ui.components

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.ui.Modifier
import com.jmcomic_next.lyqs.LiteFeatures

/**
 * 列表条目的进出场动画（1.8.0）。
 *
 * 抽成一个扩展函数，是为了让"lite 关掉动画"只在一个地方判断 ——
 * 六个调用点各写一次 `if (LiteFeatures.ENABLED)` 迟早漏一个，
 * 而漏掉的那处会以"lite 上滚动还是有点卡"的形式出现，很难归因。
 *
 * 关掉动画不只是"少一点观感开销"：条目进出场会触发**每一帧的重排与重绘**，
 * 在低端设备上这是滚动时最容易被感觉到的抖动来源。用时间换性能，
 * 这里是拿"平滑感"换"稳定感"。
 *
 * 定义在 `Modifier` 上、scope 由调用方显式传入：`animateItem()` 只在条目 lambda 里可用，
 * 而"隐式接收者"在**参数位置**（`modifier = ...`）并不成立。显式传入反而在两种位置都能用。
 */
fun Modifier.jmAnimateItem(scope: LazyItemScope): Modifier =
    if (LiteFeatures.itemAnimations) with(scope) { animateItem() } else this

/**
 * 网格版重载（`LazyGridItemScope` 与 `LazyItemScope` 是两个不同的类型，
 * 它们的 `animateItem()` 也是两个不同的扩展，所以这里必须分开写。
 */
fun Modifier.jmAnimateItem(scope: LazyGridItemScope): Modifier =
    if (LiteFeatures.itemAnimations) with(scope) { animateItem() } else this
