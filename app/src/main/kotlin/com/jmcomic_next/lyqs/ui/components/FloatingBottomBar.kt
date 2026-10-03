package com.jmcomic_next.lyqs.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 悬浮底栏里的一栏：图标 + 文字。
 *
 * 故意不带路由类型：底栏只认识「第几栏」，不认识导航。这样它就能被单测
 * （[nearestTab] / [tabSelectionWeight] 这些纯函数）和别的界面直接复用。
 */
data class BottomBarItem(val label: String, val icon: ImageVector)

/**
 * 把胶囊位置夹回 `0 .. count-1` 栏。
 *
 * 位置是 **Float**：2.5f 表示胶囊正卡在第三、第四栏中间 —— 拖动时它就该在那儿。
 */
internal fun clampPill(value: Float, count: Int): Float =
    if (count <= 0) 0f else value.coerceIn(0f, (count - 1).toFloat())

/** 松手时吸附到哪一栏：离谁近就是谁。 */
internal fun nearestTab(value: Float, count: Int): Int =
    if (count <= 0) 0 else value.roundToInt().coerceIn(0, count - 1)

/**
 * 手指落在第几栏上（KernelSU 的 `indexAt`）。
 *
 * 只在「把手指位置换算成栏号」时用；拖动过程中一律按位移增量走，不做绝对定位 ——
 * 绝对定位会让胶囊在起始瞬间跳到手指底下，手感很突兀。
 */
internal fun tabIndexAt(x: Float, tabWidth: Float, count: Int): Int =
    if (tabWidth <= 0f || count <= 0) 0 else (x / tabWidth).toInt().coerceIn(0, count - 1)

/**
 * 第 [index] 栏的"选中程度"，0 = 完全没选中，1 = 胶囊正压着它。
 *
 * 胶囊**连续**地在两栏之间移动时，两栏的颜色也连续地交接：旧的一栏慢慢褪回次要色、
 * 新的一栏慢慢染上强调色。这是"连续"最直观的一处 —— 如果颜色按整数栏号跳变，
 * 胶囊刚滑出第一栏、颜色就已经全给了第二栏，看着就是两块互不相干的东西。
 */
internal fun tabSelectionWeight(pill: Float, index: Int): Float =
    (1f - abs(pill - index)).coerceIn(0f, 1f)

/**
 * 悬浮底栏（1.5.0 按 KernelSU 的做法重做）。
 *
 * 与「贴底一条」相比，它有三件事是形态本身要求的：
 *
 *  1. **浮在内容之上**：四周留白、胶囊形，内容从它背后穿过去（内容自己留出底部空间）；
 *  2. **会滑的底**：选中态是一块跟着手指/选择走的圆角底，不是每个图标各自一层填充。
 *     位置用 **Animatable + spring** 而不是 `animateFloatAsState(tween)`：点一下是"弹"过去的，
 *     拖到一半松手是"吸"过去的（KernelSU 同样是 spring，`spring(1f, 300f, 0.5f)`）；
 *  3. **可以拖**：整条底栏上横着拖，胶囊连续跟着手指走，松手吸附到最近那一栏并切页。
 *     拖动期间不切页（KernelSU 也如此）—— 切页是吸附之后的事，否则一次拖动会连翻好几栏。
 *
 * 没有照搬 KernelSU 那套"液体玻璃"（选中内容在胶囊里再画一份、按重力转高光）：
 * 它依赖 miuix 的 backdrop/lens，本项目的玻璃是另一套实现（[GlassSurface]）。
 * 这里取的是同一观感里可达的部分：胶囊 + 连续染色 + 弹簧。
 */
@Composable
fun FloatingBottomBar(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val c = JmTheme.colors
    val motion = JmTheme.motion
    val scope = rememberCoroutineScope()
    val tabCount = items.size

    // 胶囊位置，单位是"第几栏"（可以是小数）。
    val pill = remember { Animatable(clampPill(selectedIndex.toFloat(), tabCount)) }
    var dragging by remember { mutableStateOf(false) }

    // 拖动回调活在 pointerInput 那个 lambda 里，它只在 key 变化时才重建，会一直持有
    // "创建它那一刻"的 selectedIndex / onSelect。用 rememberUpdatedState 取**当下**的值：
    // 否则拖到一半（期间可能已经切过页）再松手，判断用的是旧的选中栏，
    // 表现就是"松手停在不该停的那一栏"或"该切页却没切"。
    val latestSelectedIndex by rememberUpdatedState(selectedIndex)
    val latestOnSelect by rememberUpdatedState(onSelect)

    // 拖起来的那一下：胶囊微微放大，给"抓住了一块东西"的反馈。
    val lift by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = if (motion.springy) {
            spring(dampingRatio = 0.45f, stiffness = 900f)
        } else {
            spring(dampingRatio = 1f, stiffness = 700f)
        },
        label = "pillLift",
    )

    // 吸附/点击的弹簧。弹性风格留一点回弹，其余用临界阻尼（停住，不晃）——
    // 底栏是"定位"用的，晃两下会让人怀疑是不是没点中。
    val snapSpec = remember(motion.springy) {
        if (motion.springy) {
            spring<Float>(dampingRatio = 0.72f, stiffness = 420f)
        } else {
            spring<Float>(dampingRatio = 1f, stiffness = 300f)
        }
    }

    // 选择从**外面**变了（点栏、手势返回、深链）也要让胶囊滑过去；拖拽中不跟它抢。
    LaunchedEffect(selectedIndex, tabCount) {
        val target = clampPill(selectedIndex.toFloat(), tabCount)
        if (!dragging && pill.value != target) {
            pill.animateTo(target, snapSpec)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 浮起来，但不压到系统手势条上
            .navigationBarsPadding()
            .padding(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = Spacing.sm,
            ),
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            // 表面档位：**用中间那一档（Raised）**。
            //   Flyout（原来）浅 85.9% / 深 90.2% —— 几乎把底盖死，是「不通透」的来源；
            //   Card  浅 58.0% / 深  5.5% —— 又太透（深色下几乎只剩描边），反馈是"再实一点"。
            //   Raised 浅 73.7% / 深 72.2% —— 两套主题都在「看得出是块玻璃、但底还在」的位置。
            level = GlassLevel.Raised,
            // **真胶囊**：半径取短边的一半（percent = 50），两端是半圆。
            shape = RoundedCornerShape(percent = 50),
            tinted = true,
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp)) {
                val itemWidth = maxWidth / tabCount
                val itemWidthPx = with(LocalDensity.current) { itemWidth.toPx() }

                // 松手：吸附到最近一栏并切过去（拖动期间不切页）。
                fun settleToNearest() {
                    val target = nearestTab(pill.value, tabCount)
                    dragging = false
                    scope.launch { pill.animateTo(target.toFloat(), snapSpec) }
                    if (target != latestSelectedIndex) latestOnSelect(target)
                }

                // 滑动指示器：先画，于是它在下层
                Box(
                    modifier = Modifier
                        .offset(x = itemWidth * pill.value)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .padding(horizontal = Spacing.xs, vertical = Spacing.sm)
                        .graphicsLayer {
                            val s = 1f + 0.04f * lift
                            scaleX = s
                            scaleY = s
                        }
                        .clip(RoundedCornerShape(percent = 50))
                        .background(c.accentSoft),
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .selectableGroup()
                        .pointerInput(tabCount, itemWidthPx) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragging = true },
                                onHorizontalDrag = { change, dragAmount ->
                                    // 自己消费掉：否则同一次横拖会被下面的可点区域当成滑动
                                    change.consume()
                                    scope.launch {
                                        pill.snapTo(
                                            clampPill(pill.value + dragAmount / itemWidthPx, tabCount),
                                        )
                                    }
                                },
                                onDragEnd = { settleToNearest() },
                                onDragCancel = {
                                    dragging = false
                                    scope.launch {
                                        pill.animateTo(
                                            nearestTab(pill.value, tabCount).toFloat(),
                                            snapSpec,
                                        )
                                    }
                                },
                            )
                        },
                ) {
                    items.forEachIndexed { index, item ->
                        // 连续染色：胶囊在两栏之间时，两栏各拿一部分强调色
                        val weight = tabSelectionWeight(pill.value, index)
                        val tint = lerp(c.textSecondary, c.accent, weight)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(percent = 50))
                                .clickable { onSelect(index) }
                                .semantics {
                                    role = Role.Tab
                                    selected = index == selectedIndex
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = tint,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer {
                                        // 被胶囊压着的那一栏，拖动时跟着涨一点
                                        val s = 1f + 0.08f * weight * lift
                                        scaleX = s
                                        scaleY = s
                                    },
                            )
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                            )
                        }
                    }
                }
            }
        }
    }
}
