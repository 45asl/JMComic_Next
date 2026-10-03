package com.jmcomic_next.lyqs.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.ui.ComicTarget
import com.jmcomic_next.lyqs.ui.components.GlassLevel
import com.jmcomic_next.lyqs.ui.components.GlassSurface
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * 首页的「随机本子」浮动按钮（1.5.6）。
 *
 * ## 位置与形态照源码
 *
 * 官方 Web 客户端首页右下角有一列浮动圆按钮（`MainTopBtn.tsx`），其中一个是骰子
 * （`CasinoIcon`），点击直接进 `randomItem[0].id` 的详情页。这里沿用同样的位置与图标。
 *
 * ## 单击 / 长按
 *
 * - **单击**：直接随机跳一本 —— 骰子会**转两圈**再跳。这不是装饰：拉取要一点点时间，
 *   转起来才说明"我在办事"，否则按下到跳转之间会有一小段没有任何反馈的空档。
 * - **长按**：去随机列表页（[com.jmcomic_next.lyqs.ui.screens.random.RandomListScreen]）
 *   挑一批 —— 按用户的要求用**跳转**而不是弹窗。
 *
 * ## 屏蔽
 *
 * 随机来源 `JmRepository.randomRecommend()` **已应用标签屏蔽规则**，被屏蔽的本子抽不到；
 * 一批全被挡掉时列表页会如实说明，不留一片空白。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RandomFab(
    repo: JmRepository,
    bottomInset: Dp,
    onOpenComic: (ComicTarget) -> Unit,
    onOpenRandomList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = JmTheme.colors
    val scope = rememberCoroutineScope()
    // 骰子转动：单击时转两圈。用 Animatable 而不是 animateFloatAsState，
    // 因为要"每次点击都再转一次"，目标值需要累加而不是收敛到某个固定值。
    val spin = remember { Animatable(0f) }

    Box(modifier.padding(end = Spacing.lg, bottom = bottomInset + Spacing.md)) {
        GlassSurface(
            level = GlassLevel.Raised,
            shape = RoundedCornerShape(percent = 50),
            modifier = Modifier
                .size(52.dp)
                .combinedClickable(
                    onClick = {
                        scope.launch {
                            // 先转起来再拉取：把等待时间变成反馈
                            launch { spin.animateTo(spin.value + 720f, tween(600)) }
                            val one = runCatching { repo.bootstrap(); repo.randomRecommend() }
                                .getOrDefault(emptyList()).randomOrNull()
                            if (one != null) {
                                // 封面必须走 repo.coverUrl()：列表里的 image 是相对路径，
                                // 直接当 URL 用会加载不出来（随机页那一版就是这么错的）
                                onOpenComic(
                                    ComicTarget(one.id, repo.coverUrl(one), one.name.orEmpty()),
                                )
                            }
                        }
                    },
                    onLongClick = onOpenRandomList,
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.Casino,
                contentDescription = "随机一本（长按查看一批）；已排除屏蔽名单",
                tint = c.accent,
                modifier = Modifier
                    .padding(14.dp)
                    .rotate(spin.value),
            )
        }
    }
}
