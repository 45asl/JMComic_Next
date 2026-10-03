package com.jmcomic_next.lyqs.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.remote.dto.ListItem
import com.jmcomic_next.lyqs.ui.ComicTarget
import com.jmcomic_next.lyqs.ui.components.GlassLevel
import com.jmcomic_next.lyqs.ui.components.GlassSurface
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Radius
import com.jmcomic_next.lyqs.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * 首页的「随机本子」浮动按钮（1.5.6）。
 *
 * ## 位置与形态照源码
 *
 * 官方 Web 客户端的首页右下角有一列浮动圆按钮（`MainTopBtn.tsx`），其中一个是
 * `<CasinoIcon />`（骰子），点击直接进 `randomItem[0].id` 的详情页。这里沿用同样的
 * 位置（右侧、底部栏之上）与同样的骰子图标。
 *
 * ## 单击与长按
 *
 * - **单击**：直接随机跳一本。
 * - **长按**：推荐**一批**（让用户挑，而不是替他决定）。
 *
 * ## 屏蔽
 *
 * 随机来源是 `JmRepository.randomRecommend()`，它**已经应用标签屏蔽规则**，
 * 所以不会抽出被屏蔽的本子；万一整批都被挡掉（列表为空），按钮会如实说明，
 * 而不是静默什么都不做 —— 那是这个项目里反复出现过的"最糟的反馈"。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RandomFab(
    repo: JmRepository,
    bottomInset: androidx.compose.ui.unit.Dp,
    onOpenComic: (ComicTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = JmTheme.colors
    val scope = rememberCoroutineScope()
    var batch by remember { mutableStateOf<List<ListItem>?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Box(modifier.padding(end = Spacing.lg, bottom = bottomInset + Spacing.md)) {
        GlassSurface(
            level = GlassLevel.Raised,
            shape = RoundedCornerShape(percent = 50),
            modifier = Modifier
                .size(52.dp)
                .combinedClickable(
                    enabled = !busy,
                    onClick = {
                        // 单击：拉一次再抽一本。抽不到（例如全被屏蔽或网络失败）时给提示
                        scope.launch {
                            busy = true
                            val one = runCatching { repo.bootstrap(); repo.randomRecommend() }
                                .getOrDefault(emptyList()).randomOrNull()
                            busy = false
                            if (one == null) {
                                notice = "这次没抽到（可能是网络问题，或候选都被屏蔽了）"
                            } else {
                                onOpenComic(ComicTarget(one.id, one.image.orEmpty(), one.name.orEmpty()))
                            }
                        }
                    },
                    onLongClick = {
                        // 长按：推荐一批，交给用户挑
                        scope.launch {
                            busy = true
                            val many = runCatching { repo.bootstrap(); repo.randomRecommend() }
                                .getOrDefault(emptyList())
                            busy = false
                            if (many.isEmpty()) {
                                notice = "这批都被屏蔽规则挡住了，稍后再试"
                            } else {
                                batch = many
                            }
                        }
                    },
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.Casino,
                contentDescription = "随机一本（长按推荐一批）；已排除屏蔽名单",
                tint = c.accent,
                modifier = Modifier.padding(14.dp),
            )
        }

        notice?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 60.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .padding(Spacing.xs),
            )
        }
    }

    // 一批推荐：横向铺开，点哪本进哪本；也留一个"换一批"
    batch?.let { list ->
        Dialog(onDismissRequest = { batch = null }) {
            GlassSurface(level = GlassLevel.Raised, shape = RoundedCornerShape(Radius.lg)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = "随机推荐 ${list.size} 本",
                        style = MaterialTheme.typography.titleSmall,
                        color = c.text,
                    )
                    // 明确标注"已排除屏蔽名单"：用户看到的本子少了，应该知道原因，
                    // 而不是怀疑随机是不是坏了或者屏蔽没生效
                    Text(
                        text = "已排除你屏蔽名单里的作品",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        contentPadding = PaddingValues(vertical = Spacing.xs),
                    ) {
                        items(list, key = { it.id }) { comic ->
                            Column(
                                modifier = Modifier
                                    .size(width = 96.dp, height = 168.dp)
                                    .combinedClickable(onClick = {
                                        batch = null
                                        onOpenComic(
                                            ComicTarget(
                                                comic.id,
                                                comic.image.orEmpty(),
                                                comic.name.orEmpty(),
                                            ),
                                        )
                                    }),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                            ) {
                                AsyncImage(
                                    model = comic.image.orEmpty(),
                                    contentDescription = comic.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(136.dp)
                                        .clip(RoundedCornerShape(Radius.sm)),
                                )
                                Text(
                                    text = comic.name.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = c.text,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        TextButton(onClick = { batch = null }) { Text("关闭") }
                    }
                }
            }
        }
    }
}
