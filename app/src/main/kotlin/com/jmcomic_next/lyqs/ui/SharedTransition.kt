package com.jmcomic_next.lyqs.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * 共享元素转场的作用域。拿不到（页面不在 [SharedTransitionScope] 里）时，
 * [jmSharedElement] 会退回成"什么都不做"，而不是崩掉。
 */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** 当前导航目的地的可见性作用域：共享元素要靠它判断自己是"进入"还是"离开"。 */
val LocalNavVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * **按「触发前的位置 → 触发后的位置」做动画。**
 *
 * 这是本应用动效的组织方式（1.4.2 起）：动画不由「从右边滑进来」这类**预设方向**决定，
 * 而由这个元素**触发前在哪、触发后在哪**决定 —— 中间的过程就是这两点之间的插值。
 * 同一个 [key] 在前后两个界面里各写一次，系统就会把前者量到的矩形连续地变成后者的矩形。
 *
 * 为什么这样更对：方向是人替元素猜的（"新页面应该从右边来"），而位置是事实。
 * 猜错方向时，动画会把元素的来处说反；而按前后位置走，元素从哪来就回哪去 ——
 * 这也正是 HyperOS 那种"连续转场"的做法（点一张卡，卡从原地长成详情页）。
 *
 * 整屏页面本身**没有可依据的位置**（触发前是整屏、触发后还是整屏，位移为零），
 * 所以页面级转场只用淡入淡出，位移交给共享元素 —— 没有位置就不该硬编一个方向。
 *
 * [key] 为 null、或当前不在共享容器里时，这个修饰符不做任何事。
 */
@Composable
fun Modifier.jmSharedElement(key: String?): Modifier {
    if (key == null) return this
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavVisibilityScope.current ?: return this
    return with(shared) {
        // 用位置参数：这个重载的参数名在版本间改过（state / sharedContentState），
        // 前两个参数的身份是稳定的：内容状态 + 可见性作用域。
        this@jmSharedElement.sharedElement(
            rememberSharedContentState(key),
            visibility,
        )
    }
}

/**
 * 作品封面的共享元素键。
 *
 * 收成一个函数而不是到处拼字符串：**前后两处必须完全一致**，差一个字符共享就不成立，
 * 而且失败时是静默的（只是没有动画），很难发现。
 */
fun jmComicSharedKey(comicId: String): String = "jm-cover-$comicId"
