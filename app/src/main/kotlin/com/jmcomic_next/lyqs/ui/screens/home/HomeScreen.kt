package com.jmcomic_next.lyqs.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.components.ComicCard
import com.jmcomic_next.lyqs.ui.components.ComicRow
import com.jmcomic_next.lyqs.ui.components.ErrorBox
import com.jmcomic_next.lyqs.ui.components.GlassTopBar
import com.jmcomic_next.lyqs.ui.components.LoadingBox
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Spacing

/**
 * 首页：推荐分区（横向滚动）+ 最新上架（纵向列表，滚动到底自动续加）。
 *
 * 推荐区的数据形态是**若干带标题的区块**，不是一条长列表 ——
 * 这一点由还原源码里手写的 `InterFace.ts`（`PromoteResponse`）确认。
 */
@Composable
fun HomeScreen(
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onOpenComic: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = LocalRepository.current
    val vm: HomeViewModel = viewModel(
        factory = viewModelFactory { initializer { HomeViewModel(repo) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        GlassTopBar(
            title = "JMComic Next",
            subtitle = if (state.loading) "加载中…" else null,
            actions = {
                IconButton(onClick = { vm.refresh() }) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "刷新",
                        tint = JmTheme.colors.accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        imageVector = if (dark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = if (dark) "切换到浅色" else "切换到深色",
                        tint = JmTheme.colors.accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )

        when {
            state.loading && state.sections.isEmpty() && state.latest.isEmpty() -> LoadingBox()

            state.sections.isEmpty() && state.latest.isEmpty() && state.promoteError != null ->
                ErrorBox(
                    message = state.promoteError.orEmpty(),
                    onRetry = { vm.refresh() },
                )

            else -> HomeContent(
                state = state,
                repo = repo,
                onOpenComic = onOpenComic,
                onLoadMore = { vm.loadMore() },
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    repo: JmRepository,
    onOpenComic: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val c = JmTheme.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        // 推荐分区
        state.sections.forEach { section ->
            if (section.content.isEmpty()) return@forEach

            item(key = "sec-${section.id}-head") {
                SectionTitle(section.title.orEmpty())
            }
            item(key = "sec-${section.id}-row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(section.content, key = { it.id }) { comic ->
                        ComicCard(
                            item = comic,
                            coverUrl = repo.coverUrl(comic),
                            onClick = { onOpenComic(comic.id) },
                        )
                    }
                }
            }
        }

        if (state.promoteError != null && state.sections.isEmpty()) {
            item { ErrorBox(message = state.promoteError, onRetry = null) }
        }

        // 最新上架
        item(key = "latest-head") { SectionTitle("最新上架") }

        if (state.latestError != null && state.latest.isEmpty()) {
            item { ErrorBox(message = state.latestError) }
        }

        items(state.latest, key = { "latest-${it.id}" }) { comic ->
            Box(Modifier.padding(horizontal = Spacing.lg)) {
                ComicRow(
                    item = comic,
                    coverUrl = repo.coverUrl(comic),
                    onClick = { onOpenComic(comic.id) },
                )
            }
        }

        if (state.latest.isNotEmpty()) {
            item(key = "latest-more") { LoadMoreRow(state.loadingMore, onLoadMore) }
        }
    }
}

/**
 * 列表末尾的续加触发器。
 *
 * 不用「滚动位置判断」而是把触发器本身渲染成列表项并在其可见时请求下一页 ——
 * LazyColumn 只会组合可见项，因此这等价于「滚到底自动加载」，且不需要额外的
 * 滚动状态监听。首次组合即触发，所以首次进入会立刻请求第二页，这是可接受的。
 */
@Composable
private fun LoadMoreRow(loading: Boolean, onLoadMore: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = JmTheme.colors.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
            )
        } else {
            androidx.compose.runtime.LaunchedEffect(Unit) { onLoadMore() }
            Text(
                text = "上滑加载更多",
                style = MaterialTheme.typography.labelSmall,
                color = JmTheme.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = JmTheme.colors.text,
        modifier = Modifier.padding(horizontal = Spacing.lg),
    )
}
