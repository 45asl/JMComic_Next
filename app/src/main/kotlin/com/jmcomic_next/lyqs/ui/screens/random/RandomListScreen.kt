package com.jmcomic_next.lyqs.ui.screens.random

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import coil3.compose.AsyncImage
import com.jmcomic_next.lyqs.data.remote.dto.ListItem
import com.jmcomic_next.lyqs.ui.ComicTarget
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.LocalBottomBarInset
import com.jmcomic_next.lyqs.ui.components.GlassTopBar
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Radius
import com.jmcomic_next.lyqs.ui.theme.Spacing

/**
 * 随机推荐**一批**（1.5.6）。
 *
 * 长按首页那颗骰子会到这里 —— 按用户的要求用**跳转成一个列表**，
 * 而不是弹一层对话框：列表可以滚、可以换一批、看中了再点进去，
 * 也不会把首页压在底下。
 *
 * 数据来自 `randomRecommend()`，它**已经应用标签屏蔽规则**，所以被屏蔽的本子不会出现在这里；
 * 这一点在页面上**明确写出来**，否则用户看到数量变少会怀疑随机坏了。
 */
@Composable
fun RandomListScreen(
    onBack: () -> Unit,
    onOpenComic: (ComicTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = JmTheme.colors
    val repo = LocalRepository.current
    var items_ by remember { mutableStateOf<List<ListItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // 换一批：改这个值触发重新拉取
    var round by remember { mutableStateOf(0) }

    LaunchedEffect(round) {
        loading = true
        error = null
        runCatching { repo.bootstrap(); repo.randomRecommend() }
            .onSuccess { items_ = it }
            .onFailure { error = it.message?.takeIf { m -> m.isNotBlank() } ?: "网络问题" }
        loading = false
    }

    Column(modifier.fillMaxSize()) {
        GlassTopBar(
            title = "随机推荐",
            subtitle = if (items_.isEmpty()) null else "已排除你屏蔽名单里的作品",
            navigation = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = c.accent)
                }
            },
            actions = {
                TextButton(onClick = { round += 1 }) { Text("换一批") }
            },
        )
        when {
            loading -> Hint("正在随机…")
            error != null -> Hint("没拿到：$error")
            items_.isEmpty() -> Hint("这次没抽到（可能候选都被屏蔽名单挡住了，或网络不通）。")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.lg, end = Spacing.lg,
                    bottom = LocalBottomBarInset.current + Spacing.lg,
                ),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(items_, key = { it.id }) { comic ->
                    Column(
                        modifier = Modifier.clickable {
                            onOpenComic(
                                ComicTarget(comic.id, comic.image.orEmpty(), comic.name.orEmpty()),
                            )
                        },
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    ) {
                        AsyncImage(
                            model = comic.image.orEmpty(),
                            contentDescription = comic.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.72f)
                                .clip(RoundedCornerShape(Radius.md)),
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
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = JmTheme.colors.textSecondary,
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}
