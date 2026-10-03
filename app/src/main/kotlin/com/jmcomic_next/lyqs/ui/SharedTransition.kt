package com.jmcomic_next.lyqs.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.PlaceholderSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.jmcomic_next.lyqs.ui.theme.JmTheme

/**
 * 共享元素转场的作用域。拿不到（页面不在 [SharedTransitionScope] 里）时，
 * [jmSharedElement] 会退回成"什么都不做"，而不是崩掉。
 */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** 当前导航目的地的可见性作用域：共享元素要靠它判断自己是"进入"还是"离开"。 */
val LocalNavVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * **这一页的封面参不参与共享元素。** 默认参与；只有 [JmNavHost] 在 Tab 横滑期间
 * 把它置为 false（见那里的 `slidingTabRoutes`）。
 *
 * 为什么需要这个开关：Tab 之间横滑时，出页与入页**同时**活在组合树里（一个在
 * PostExit、一个在 PreEnter），如果同一部作品在两边都有一张卡，两处就会挂着
 * **同一个 key**。共享元素只看 key，不看"这两个 key 是不是同一页里的一对兄弟" ——
 * 于是它会把出页那张封面当成"触发前的位置"、入页那张当成"触发后的位置"，
 * 播一次"从出页的卡飞进入页的卡"的动画（实测日志：
 * `MATCH key=jm-cover-1437141 found=true` 在两个不同的 visibility scope 上同时出现，
 * `SharedTransitionScope.isTransitionActive=true`）。
 *
 * 那不是任何一种"前后位置关系"：用户点的是底栏，页面整体在横滑，两个位置之间
 * 没有"这张卡变成了那张卡"的因果。所以横滑中的两页都退出共享元素 ——
 * 少挂一个 key 就不会有匹配（见 SharedElement.updateMatch 的 hasVisibleContent 判定），
 * 也不会留下任何 overlay 残影。
 */
val LocalSharedElementEnabled = compositionLocalOf { true }

/**
 * 把 [LocalSharedElementEnabled]（这一页参不参与共享元素）翻译成共享元素库认的开关。
 *
 * 用库自己的 [SharedTransitionScope.SharedContentConfig.isEnabled]，而不是"少挂 key"：
 * 这是官方给"动态开关共享元素"的口子（`DynamicallyEnableSharedElements` 那一组样例），
 * key 与 [rememberSharedContentState] 的槽位都保持原样，页面上不会因为 key 的有无
 * 而在组合树上多一次增删。
 *
 * [SharedTransitionScope.SharedContentConfig.shouldKeepEnabledForOngoingAnimation]
 * 改成 false 是有意的：库默认"已经在飞的动画会一直有效到最后"（免得误杀进行中的动画）。
 * 但这里关掉它的理由正相反 —— 万一横滑的那一帧里两边都已经配上了匹配（我们的开关
 * 与 NavHost 的内容列表来自同一次 `visibleEntries` 更新，正常不会错开，但不能假定），
 * 关掉这一条能让它当场作废，而不是把那一次"两页之间飞封面"播完。
 */
@Composable
private fun sharedContentConfig(): SharedTransitionScope.SharedContentConfig {
    val enabled = LocalSharedElementEnabled.current
    return remember(enabled) {
        object : SharedTransitionScope.SharedContentConfig {
            override val SharedTransitionScope.SharedContentState.isEnabled: Boolean
                get() = enabled
            override val shouldKeepEnabledForOngoingAnimation: Boolean
                get() = false
        }
    }
}

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
    // **和页面转场用同一套时长与曲线。**
    // 共享元素默认走的是自己的 spring；如果页面在用 tween 淡入、封面在用 spring 飞，
    // 两条时间线不一致，看起来就是两段互不相干的动画（反馈里的"不是从头到尾一个完整动画"）。
    // 绑到同一个 MotionSpec 之后，封面到位与页面淡完是同一时刻。
    val motion = JmTheme.motion
    val bounds = remember(motion.base, motion.enter) {
        BoundsTransform { _, _ -> tween(durationMillis = motion.base, easing = motion.enter) }
    }
    return with(shared) {
        // 用位置参数：这个重载的参数名在版本间改过（state / sharedContentState），
        // 前三个参数的身份是稳定的：内容状态 + 可见性作用域 + 边界动画。
        //
        // 后面两个是**这一版要的"补全空缺"语义**，所以不再靠默认值：
        //
        //  · renderInOverlayDuringTransition = true（默认也是 true，这里钉死）：一帧里封面只有**一份**，
        //    画在 SharedTransitionScope 的 overlay 里、按动画后的矩形绘制；它原来所在的
        //    位置（列表里的那张卡片）留下的是一块**空缺**，不是"另一份封面"。
        //    也就是说飞回来的封面是**补进**空缺，而不是叠在目标位置自己画的封面上。
        //    实测（逐帧像素）：列表那张卡片的位置在飞行期间是纯背景（某帧该矩形内
        //    灰度均值 233/标准差 0，而同帧封面本体在别处、均值 ~150/标准差 ~60），
        //    直到封面落进去为止。
        //
        //  · placeholderSize = ContentSize（默认也是它）：空缺**保留封面原来那么大的地方**。
        //    若改成 AnimatedSize，列表会在每一帧按飞过来的尺寸重新排版，卡片会跟着抖，
        //    那不是"留一个空缺"，是"空缺自己在长大"。
        //
        // 为什么不用 sharedBounds：它要求前后两块内容各自 enter/exit 交叉淡入淡出，
        // 一处变成两份内容同时存在（同一张封面在同一个矩形里叠着淡），反而更像"覆盖安装"；
        // 而且进入方向（列表 → 详情，目前观感是对的）也会被这套交叉淡化改掉。
        val contentState = rememberSharedContentState(key, sharedContentConfig())
        this@jmSharedElement.sharedElement(
            contentState,
            visibility,
            bounds,
            placeholderSize = PlaceholderSize.ContentSize,
            renderInOverlayDuringTransition = true,
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

/**
 * **退出时立刻隐藏自己的内容**（不做淡出）。
 *
 * 详情页的退出规则是"内容直接消失、只有封面在动"：封面走的是共享元素的 overlay
 * （见 [jmSharedElement]），跟本页淡不淡没有关系；本页自己的淡出只会让标题、作者、
 * 标签、章节、按钮这些**不该动的东西**跟着慢慢化掉 —— 而它们本该在按下的那一刻就没了。
 *
 * 为什么不用最直接的 `ExitTransition.None`（1.4.3 实测后放弃）：
 * 把详情页目的地的 popExit 换成 None 之后，**目的地并不会当场消失，而是"冻"在原样
 * 被留了两帧**（逐帧像素：退出后第 1、2 帧里详情页那几个按钮仍与退出前**完全一致**、
 * 满不透明度），而共享元素的时钟已经在这两帧里走过大半 —— 封面第一次被画出来时
 * 已经走完约 80% 的路程（起始位置那几帧根本没被看见）。也就是说：页面级动画确实没了，
 * 但"封面完整地飞回去"也跟着没了。所以这里反过来做：**保留目的地的转场**（让共享元素
 * 与页面共用同一条时间线），只把本页内容在退出中藏掉。
 *
 * 条件里那句 [SharedTransitionScope.isTransitionActive] 不能省：它正好是"共享元素
 * 正在 overlay 里画着"的时刻。没有它时，详情 → 阅读 这类**没有共享元素**的退出会
 * 变成"本页瞬隐 + 下一页淡入"，中间露出背景，比原来的交叉淡出更差；有共享元素在飞时
 * 则相反 —— 藏掉本页只是收走"另一份内容"，屏幕上并不空（封面还在飞）。
 *
 * 不在 [SharedTransitionScope] 里、或当前目的地没有可见性作用域时，这个修饰符不做任何事。
 */
@Composable
fun Modifier.jmVanishWhenLeaving(): Modifier {
    val visibility = LocalNavVisibilityScope.current ?: return this
    val shared = LocalSharedTransitionScope.current
    // 目的地正在离开（目标状态不再是 Visible）—— 读的是 Transition 的状态，会自动重组。
    val leaving = visibility.transition.targetState != EnterExitState.Visible
    if (!leaving || shared?.isTransitionActive != true) return this
    return this.graphicsLayer { alpha = 0f }
}
