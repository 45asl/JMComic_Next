package com.jmcomic_next.lyqs.ui.screens.home

import com.jmcomic_next.lyqs.data.remote.dto.NotificationItem
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import com.jmcomic_next.lyqs.ui.LocalTagBlocker
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jmcomic_next.lyqs.ui.ComicTarget
import com.jmcomic_next.lyqs.ui.jmComicSharedKey
import com.jmcomic_next.lyqs.ui.LocalBottomBarInset
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.remote.dto.PromoteSection
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.components.ComicCard
import com.jmcomic_next.lyqs.ui.components.ComicRow
import com.jmcomic_next.lyqs.ui.components.ErrorBox
import com.jmcomic_next.lyqs.ui.components.GlassTopBar
import com.jmcomic_next.lyqs.ui.components.GlassSurface
import com.jmcomic_next.lyqs.ui.components.GlassLevel
import com.jmcomic_next.lyqs.ui.components.LoadMoreFooter
import com.jmcomic_next.lyqs.ui.components.LoadingBox
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Spacing
import com.jmcomic_next.lyqs.ui.theme.Radius

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
    onOpenComic: (ComicTarget) -> Unit,
    onOpenSection: (PromoteSection) -> Unit,
    onOpenWeek: () -> Unit,
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
                // 周刊入口（官方在顶栏放的就是日历图标）
                IconButton(onClick = onOpenWeek) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = "周刊",
                        tint = JmTheme.colors.accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
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

            // 随机本子（1.5.6）：源码在首页右下角放了一个骰子浮动按钮，
            // 点了直接进一本随机作品。这里只包在"内容已就绪"这一支里 ——
            // 加载中/加载失败时不该出现一个点了没反应的按钮。
            else -> Box(modifier = Modifier.fillMaxSize()) {
                HomeContent(
                    state = state,
                    repo = repo,
                    onOpenComic = onOpenComic,
                    onOpenSection = onOpenSection,
                    onLoadMore = { vm.loadMore() },
                    onRetryLoadMore = { vm.retryLoadMore() },
                )
                RandomFab(
                    repo = repo,
                    bottomInset = LocalBottomBarInset.current,
                    onOpenComic = onOpenComic,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    repo: JmRepository,
    onOpenComic: (ComicTarget) -> Unit,
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

    // 1.5.3：「你追的连载里哪些更新了」。
    //
    // 数据来自**服务端的通知**（`comic_follow`）—— 作品更新时服务端就写了一条通知，
    // 未读的那批里带的作品 id 就是要打标记的。**不要**在客户端拿阅读时间去猜：
    // 那既不准（分不清"更新的是不是我看过的那话"）又白白多一次判断。
    //
    // 仍然**不认分区标题、也不认分区 id**：哪一行里有这些作品，那一行标题旁就显示计数，
    // 所以服务端改标题文案（实测带「→右滑看更多→」）不会让标记消失。
    val repoForNotify = LocalRepository.current
    val loggedIn = repoForNotify.auth.isLoggedIn
    val updatedIds by produceState(initialValue = emptySet<String>(), loggedIn) {
        value = if (!loggedIn) {
            emptySet()
        } else {
            runCatching {
                repoForNotify.notifications(type = NotificationItem.TYPE_COMIC_FOLLOW)
                    .list.filterNot { it.isRead }
                    .flatMap { it.followedUpdates() }
                    .mapNotNull { it.comicIdText }
                    .toSet()
            }.getOrDefault(emptySet())
        }
    }

    // **同一部作品出现在两个分区里时，两张卡不挂共享键。**
    //
    // 首页是唯一"一屏里有好几条列表"的页面（分类 / 搜索 / 更多 / 周刊各自只有一条列表，
    // 而它们都用 `items(key = { it.id })`，同一个列表里不可能有两个相同的 id）。
    // 两条分区同时可见时，同一部作品的两张卡会挂着**同一个共享键**，共享元素只看 key、
    // 不看"这两处是不是同一页里的兄弟"，于是会把这两张卡当成"一张变成了另一张"来播 ——
    // 明明只是滚动列表，却有一张封面从这张卡飞到那张卡（见 LocalSharedElementEnabled）。
    //
    // 重复出现的作品索性不参与：它本来就有两张，往哪张飞都是错的。
    // 代价是"从这两张卡进详情"没有封面动画 —— 这是按「重复的作品不放动画」
    // 这条规则有意为之的，非重复作品的"列表 → 详情"完全不受影响。
    val tagBlocker = LocalTagBlocker.current
    // 标签屏蔽（1.5.1）：列表接口不返回标签，命中集合是**异步**补上来的。
    // 没有解析器（或没有标签规则）时这里恒为空集合，列表照常显示。
    val hiddenIds by remember(tagBlocker) {
        tagBlocker?.hidden ?: MutableStateFlow(emptySet<String>())
    }.collectAsStateWithLifecycle()

    val duplicatedComicIds = remember(sections) {
        sections.asSequence()
            .flatMap { it.content.asSequence() }
            .groupingBy { it.id }
            .eachCount()
            .filterValues { it > 1 }
            .keys
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // 底栏是悬浮的：内容从它后面穿过去，这里留出能把它滚出去的空间
        contentPadding = PaddingValues(bottom = Spacing.xxl + LocalBottomBarInset.current),
        // 分区之间要拉开：原来 16dp，横向卡片行挨得太近，看不出「这是另一块」
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        // 推荐分区
        sections.forEach { section ->
            if (section.content.isEmpty()) return@forEach

            item(key = "sec-${section.id}-head") {
                SectionTitle(
                    title = section.title.orEmpty(),
                    onMore = { onOpenSection(section) },
                    updatedCount = section.content.count { it.id in updatedIds },
                )
            }
            item(key = "sec-${section.id}-row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    // 命中标签规则的作品直接滤掉（标签是异步取回来的，所以列表会随结果收敛）。
                    items(
                        items = section.content.filterNot { it.id in hiddenIds },
                        key = { it.id },
                    ) { comic ->
                        // 条目可见才去取它的详情（拿标签）。**没有标签规则时 request 内部首行就返回**，
                        // 一个请求都不会发；并发上限与缓存都在解析器里。
                        LaunchedEffect(comic.id) { tagBlocker?.request(comic.id) }
                        // 封面地址顺手算一次：卡片自己要用，导航参数也要用
                        val cover = repo.coverUrl(comic)
                        ComicCard(
                            item = comic,
                            coverUrl = cover,
                            // 封面与标题跟着路由一起走：详情页第一帧才有"触发后的位置"
                            onClick = {
                                onOpenComic(ComicTarget(comic.id, cover, comic.name.orEmpty()))
                            },
                            sharedKey = if (comic.id in duplicatedComicIds) {
                                null
                            } else {
                                jmComicSharedKey(comic.id)
                            },
                            updated = comic.id in updatedIds,
                            // 被滤掉时下面的条目**平滑上移**而不是跳一下
                            modifier = Modifier.animateItem(),
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
                    // 与分区标题旁的「更多」用同一种玻璃胶囊，而不是裸文字按钮
                    GlassSurface(
                        level = GlassLevel.Card,
                        shape = RoundedCornerShape(Radius.pill),
                        onClick = { visibleSections += SECTION_STEP },
                    ) {
                        Text(
                            text = "展开更多分区（还有 $remaining 个）",
                            style = MaterialTheme.typography.labelSmall,
                            color = JmTheme.colors.accent,
                            modifier = Modifier.padding(
                                horizontal = Spacing.lg,
                                vertical = Spacing.sm,
                            ),
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
            val cover = repo.coverUrl(comic)
            Box(Modifier.padding(horizontal = Spacing.lg)) {
                ComicRow(
                    item = comic,
                    coverUrl = cover,
                    onClick = {
                        onOpenComic(ComicTarget(comic.id, cover, comic.name.orEmpty()))
                    },
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
private fun SectionTitle(
    title: String,
    onMore: (() -> Unit)? = null,
    /** 这一行里"你追的、且更新了"的作品数。0 表示不显示标记。 */
    updatedCount: Int = 0,
) {
    val c = JmTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            // 服务端把操作提示写进了标题（实测「连载更新→右滑看更多→」），
            // 直接显示就是把这串箭头当成内容摆出来。这里只取它前面的标题部分。
            text = title.substringBefore("→").trim().ifEmpty { title },
            style = MaterialTheme.typography.titleLarge,
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (updatedCount > 0) {
            // 只在**真的有**更新时才出现：没有更新的日子这一行保持干净，
            // 否则每天都挂着一个「0 部更新」，提示很快就没人看了
            Text(
                text = "你追的 $updatedCount 部有更新",
                style = MaterialTheme.typography.labelSmall,
                color = c.textOnAccent,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(c.accent)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            )
        }
        if (onMore != null) {
            // 玻璃小胶囊而不是裸文字按钮：与卡片同一套表面语言，点击区域也更大
            GlassSurface(
                level = GlassLevel.Card,
                shape = RoundedCornerShape(Radius.pill),
                onClick = onMore,
            ) {
                Text(
                    text = "更多",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.accent,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
        }
    }
}
