package com.jmcomic_next.lyqs.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.remote.dto.PromoteSection
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.components.ComicCard
import com.jmcomic_next.lyqs.ui.components.ComicRow
import com.jmcomic_next.lyqs.ui.components.ErrorBox
import com.jmcomic_next.lyqs.ui.components.GlassTopBar
import com.jmcomic_next.lyqs.ui.components.LoadMoreFooter
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
    onOpenSection: (PromoteSection) -> Unit,
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
                onOpenSection = onOpenSection,
                onLoadMore = { vm.loadMore() },
                onRetryLoadMore = { vm.retryLoadMore() },
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    repo: JmRepository,
    onOpenComic: (String) -> Unit,
    onOpenSection: (PromoteSection) -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
) {
    val c = JmTheme.colors

    // 服务端的 promote 会一次性回几十个分区（实测 40+，且标题大量重复），
    // 全部铺成横向行会让首页变成一条没有尽头的滚轴，也失去了「推荐」的意义。
    // 因此默认只展开前几个，其余按需追加。
    var visibleSections by rememberSaveable { mutableIntStateOf(INITIAL_SECTIONS) }
    val sections = state.sections.filter { it.content.isNotEmpty() }.take(visibleSections)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        // 推荐分区
        sections.forEach { section ->
            if (section.content.isEmpty()) return@forEach

            item(key = "sec-${section.id}-head") {
                SectionTitle(
                    title = section.title.orEmpty(),
                    onMore = { onOpenSection(section) },
                )
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

        // 还有未展开的分区时给一个入口，避免默认就堆出几十行
        val remaining = state.sections.count { it.content.isNotEmpty() } - sections.size
        if (remaining > 0) {
            item(key = "more-sections") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                    contentAlignment = Alignment.Center,
                ) {
                    TextButton(onClick = { visibleSections += SECTION_STEP }) {
                        Text(
                            text = "展开更多分区（还有 $remaining 个）",
                            color = JmTheme.colors.accent,
                        )
                    }
                }
            }
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
            item(key = "latest-more") {
                LoadMoreFooter(
                    loading = state.loadingMore,
                    error = state.loadMoreError,
                    exhausted = state.latestExhausted,
                    onLoadMore = onLoadMore,
                    onRetry = onRetryLoadMore,
                )
            }
        }
    }
}

/** 首页默认展开的推荐分区数。 */
private const val INITIAL_SECTIONS = 3

/** 每次「展开更多」追加的分区数。 */
private const val SECTION_STEP = 6

/**
 * 分区标题 + 「更多」入口。
 *
 * 「更多」是必要的而不只是装饰：首页每个分区只给十几条（`promote` 的分区字段就那么长），
 * 而服务端为每个分区准备了完整列表（`promote_list`）。少了这个入口，
 * 分区标题里那句「→右滑看更多→」（实测第一个分区的标题就是这个）就成了空话 ——
 * 用户能滑到的只有首页带出来的那一小段。
 */
@Composable
private fun SectionTitle(title: String, onMore: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = JmTheme.colors.text,
            modifier = Modifier.weight(1f),
        )
        if (onMore != null) {
            TextButton(onClick = onMore) {
                Text("更多 ›", color = JmTheme.colors.accent)
            }
        }
    }
}
