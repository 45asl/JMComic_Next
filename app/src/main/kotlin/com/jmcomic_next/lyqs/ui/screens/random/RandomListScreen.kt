package com.jmcomic_next.lyqs.ui.screens.random

import com.jmcomic_next.lyqs.ui.components.jmAnimateItem
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import com.jmcomic_next.lyqs.JmApp
import com.jmcomic_next.lyqs.data.FavoriteTags
import com.jmcomic_next.lyqs.data.RandomRanking
import com.jmcomic_next.lyqs.data.prefs.AppPrefs
import com.jmcomic_next.lyqs.data.remote.dto.ListItem
import com.jmcomic_next.lyqs.ui.ComicTarget
import com.jmcomic_next.lyqs.ui.LocalBottomBarInset
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.LocalTagBlocker
import com.jmcomic_next.lyqs.ui.components.GlassTopBar
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Radius
import com.jmcomic_next.lyqs.ui.theme.Spacing

/** 随机页的两种版式（1.5.6）。 */
private const val LAYOUT_GRID = "grid"
private const val LAYOUT_LIST = "list"

/**
 * 随机推荐**一批**（1.5.6）。
 *
 * 长按首页那颗骰子会到这里 —— 按用户的要求用**跳转成一个列表**，而不是弹一层对话框。
 *
 * 两种版式（用户可切换，选择记在 prefs 里）：
 * - **网格**：只看封面，一屏看最多。
 * - **列表（封面 + 详情）**：多看一行作者与分类，适合慢慢挑。
 *
 * 封面 URL 一律走 `JmRepository.coverUrl()` —— 列表项里的 `image` 是**相对路径**，
 * 直接当 URL 用会加载不出来（这正是用户报的那个 bug）。
 *
 * 数据来自 `randomRecommend()`，它**已经应用标签屏蔽规则**；这一点在页面上明确写出来。
 */
@Composable
fun RandomListScreen(
    onBack: () -> Unit,
    onOpenComic: (ComicTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = JmTheme.colors
    val context = LocalContext.current
    val repo = LocalRepository.current
    val prefs = remember(context) { AppPrefs(context) }
    var layout by remember { mutableStateOf(prefs.randomLayout) }
    var items_ by remember { mutableStateOf<List<ListItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableStateOf(0) }

    // ---- 个性化排序：按"收藏里出现最多的标签"给这批随机排序 ----
    //
    // 标签拿不到现成的（列表接口不下发），所以每个候选都要额外读一次详情。
    // 于是顺序是"先按原始顺序显示、标签陆续到了再重排"，而不是等全部读完才显示 ——
    // 后者会让用户盯着空屏等一串请求。
    val app = remember(context) { context.applicationContext as JmApp }
    val tagBlocker = LocalTagBlocker.current
    val rules by app.blockStore.state.collectAsStateWithLifecycle()
    val favoriteTags = remember(context) { FavoriteTags(context) }
    var favoriteTagCounts by remember { mutableStateOf(favoriteTags.cached()) }
    // 已读到的标签：id -> 标签集合。用 snapshot 的 state map，增量写入不会丢更新
    val knownTags = remember { mutableStateMapOf<String, Set<String>>() }

    // 只给**屏幕上可见**的条目读标签（1.6.0）。
    // 原来是整批全读：一批二十几条就是二十几个详情请求，而用户可能只看前三行。
    // 首页那套（TagBlockResolver）本来就是"条目可见才请求"，这里对齐它。
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val visibleIds by remember {
        derivedStateOf {
            // 两种版式的 layoutInfo 类型不同（网格/列表），所以分开取再合并
            val ids = if (layout == LAYOUT_GRID) {
                gridState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }
            } else {
                listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }
            }
            ids.toSet()
        }
    }

    // 收藏标签统计：缓存在一周内就不重扫（否则每次进这一页都要打几十个详情请求）
    LaunchedEffect(round) {
        val now = System.currentTimeMillis()
        favoriteTagCounts = if (FavoriteTags.isFresh(favoriteTags.cachedAt(), now)) {
            favoriteTags.cached()
        } else {
            runCatching { favoriteTags.refresh(repo) }.getOrDefault(favoriteTags.cached())
        }
    }

    // 逐条读标签：**只读可见的**，并发上限 3（突发一批请求不该把服务端和自己都压住）。
    // 滚动时可见集合变化会再次触发，于是新滚进来的条目按需补读 —— 总量被"屏幕上最多几十条"封顶。
    LaunchedEffect(visibleIds) {
        val todo = visibleIds.filterNot { knownTags.containsKey(it) }
        if (todo.isEmpty()) return@LaunchedEffect
        val gate = Semaphore(3)
        coroutineScope {
            todo.map { id ->
                async {
                    // 先问共享缓存：首页/搜索/分类读过的作品这里不该再读一次
                    val cached = tagBlocker?.cachedTags(id)
                    val tags = cached ?: gate.withPermit {
                        withContext(Dispatchers.IO) {
                            runCatching { repo.album(id).tags.toSet() }.getOrNull()
                        }?.also { fetched ->
                            // 回填：本次运行内别处再用到这部作品就不用再读；命中屏蔽规则也会随之收敛
                            if (fetched.isNotEmpty()) tagBlocker?.rememberTags(id, fetched)
                        }
                    }
                    // 只写自己这一条，不整体替换 —— 整体替换会让先到的结果被后到的覆盖
                    if (tags != null) knownTags[id] = tags
                }
            }.awaitAll()
        }
    }

    // 排序：命中收藏标签多的靠前；命中屏蔽规则的整条移除（"飞起来"）
    val ordered = remember(items_, knownTags.toMap(), favoriteTagCounts, rules) {
        RandomRanking.rank(
            items = items_,
            tagsOf = { knownTags[it.id] },
            favoriteTags = favoriteTagCounts,
            isBlocked = { tags -> rules.hitsTags(tags).isNotEmpty() },
        )
    }

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
            subtitle = when {
                items_.isEmpty() -> null
                favoriteTagCounts.isEmpty() -> "已排除你屏蔽名单里的作品"
                // 说明排序依据：用户看到顺序变了应该知道为什么
                else -> "已排除屏蔽名单 · 按你收藏偏好的标签排序"
            },
            navigation = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = c.accent)
                }
            },
            actions = {
                // 版式切换：图标显示"切过去会变成什么"，而不是当前是什么
                IconButton(onClick = {
                    layout = if (layout == LAYOUT_GRID) LAYOUT_LIST else LAYOUT_GRID
                    prefs.randomLayout = layout
                }) {
                    if (layout == LAYOUT_GRID) {
                        Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = "切换成列表", tint = c.accent)
                    } else {
                        Icon(Icons.Filled.GridView, contentDescription = "切换成网格", tint = c.accent)
                    }
                }
                TextButton(onClick = { round += 1 }) { Text("换一批") }
            },
        )
        when {
            loading -> Hint("正在随机…")
            error != null -> Hint("没拿到：$error")
            items_.isEmpty() -> Hint("这次没抽到（可能候选都被屏蔽名单挡住了，或网络不通）。")
            layout == LAYOUT_GRID -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.lg, end = Spacing.lg,
                    bottom = LocalBottomBarInset.current + Spacing.lg,
                ),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(ordered, key = { it.id }) { comic ->
                    Column(
                        // 命中屏蔽的条目会从这里消失；animateItem 让它"飞出去"而不是瞬间不见
                        modifier = Modifier.jmAnimateItem(this)
                            .clickable { onOpenComic(target(repo, comic)) },
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    ) {
                        AsyncImage(
                            model = repo.coverUrl(comic),
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
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.lg, end = Spacing.lg, top = Spacing.sm,
                    bottom = LocalBottomBarInset.current + Spacing.lg,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(ordered, key = { it.id }) { comic ->
                    Row(
                        modifier = Modifier.fillMaxWidth().jmAnimateItem(this)
                            .clickable { onOpenComic(target(repo, comic)) },
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        AsyncImage(
                            model = repo.coverUrl(comic),
                            contentDescription = comic.name,
                            modifier = Modifier
                                .width(82.dp)
                                .height(114.dp)
                                .clip(RoundedCornerShape(Radius.sm)),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                        ) {
                            Text(
                                text = comic.name.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.text,
                                maxLines = 2,
                            )
                            comic.author?.takeIf { it.isNotBlank() }?.let {
                                Text(text = it, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                            }
                            comic.category?.title?.takeIf { it.isNotBlank() }?.let {
                                Text(text = it, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 详情页要的封面同样是完整 URL，所以这里也走 `coverUrl()`。 */
private fun target(repo: com.jmcomic_next.lyqs.data.JmRepository, comic: ListItem): ComicTarget =
    ComicTarget(comic.id, repo.coverUrl(comic), comic.name.orEmpty())

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
