package com.jmcomic_next.lyqs.ui.screens.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.remote.dto.CategoryNode
import com.jmcomic_next.lyqs.data.remote.dto.ListItem
import com.jmcomic_next.lyqs.data.remote.dto.SubCategory
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.components.ComicCard
import com.jmcomic_next.lyqs.ui.components.ErrorBox
import com.jmcomic_next.lyqs.ui.components.GlassLevel
import com.jmcomic_next.lyqs.ui.components.GlassSurface
import com.jmcomic_next.lyqs.ui.components.GlassTopBar
import com.jmcomic_next.lyqs.ui.components.LoadingBox
import com.jmcomic_next.lyqs.ui.components.MessageState
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Radius
import com.jmcomic_next.lyqs.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 分类筛选的排序档位。
 *
 * 比搜索多出月榜与周榜 —— 官方 `CatSortData` 里定义了而搜索的 `SearchSortData` 没有，
 * 因此不能复用搜索的枚举。
 */
enum class CategorySort(val key: String, val label: String) {
    LATEST("", "最新"),
    MOST_HEARTS("tf", "最多爱心"),
    TOTAL_RANK("mv", "总排行"),
    MONTH_RANK("mv_m", "月排行"),
    WEEK_RANK("mp_w", "周排行"),
}

data class CategoryUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val categories: List<CategoryNode> = emptyList(),
    /**
     * 分组标签（`categories` 响应的 `blocks`）。
     *
     * 官方用它做「主题 A 漫 / 角色」这类标签组（`DialogModal.tsx` 里把每组的 `content`
     * 逐个渲染成跳 `/search?filter=<tag>` 的入口），这里放在结果网格下方。
     */
    val blocks: List<com.jmcomic_next.lyqs.data.remote.dto.CategoryBlock> = emptyList(),
    /** 加载失败时用于兜底的标签（来自 `hot_tags`）。 */
    val fallbackTags: List<String> = emptyList(),
    val parent: CategoryNode? = null,
    val sub: SubCategory? = null,
    val sort: CategorySort = CategorySort.LATEST,
    val loadingList: Boolean = false,
    val listError: String? = null,
    val comics: List<ListItem> = emptyList(),
    val loadingMore: Boolean = false,
    val total: Int = 0,
)

class CategoryViewModel(private val repo: JmRepository) : ViewModel() {

    private val _state = MutableStateFlow(CategoryUiState())
    val state: StateFlow<CategoryUiState> = _state.asStateFlow()

    private var page = 1

    init {
        loadTree()
    }

    /** 拉分类树；失败则退回热门标签，保证这一屏不会是死的。 */
    fun loadTree() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.bootstrap() }
            val tree = runCatching { repo.categories() }
            val tags = if (tree.isFailure) runCatching { repo.hotTags() }.getOrDefault(emptyList()) else emptyList()

            val first = tree.getOrNull()?.categories?.firstOrNull()
            _state.update {
                it.copy(
                    loading = false,
                    error = tree.exceptionOrNull()?.message,
                    categories = tree.getOrNull()?.categories.orEmpty(),
                    blocks = tree.getOrNull()?.blocks.orEmpty(),
                    fallbackTags = tags,
                    parent = first,
                    sub = null,
                )
            }
            if (first != null) loadList()
        }
    }

    fun selectParent(node: CategoryNode) {
        if (_state.value.parent?.slug == node.slug) return
        // 换父分类时子分类选择必须清掉，否则会带着上一个父的子项去请求
        _state.update { it.copy(parent = node, sub = null) }
        loadList()
    }

    fun selectSub(sub: SubCategory?) {
        _state.update { it.copy(sub = sub) }
        loadList()
    }

    fun setSort(sort: CategorySort) {
        if (_state.value.sort == sort) return
        _state.update { it.copy(sort = sort) }
        loadList()
    }

    /**
     * 分类筛选的 `c` 参数：父分类传 slug，子分类传 `<父 slug>_<子 slug>`。
     *
     * 返回**空串是合法状态**（分类树第一项「最新A漫」的 slug 就是空串，语义为「不筛选」），
     * 由仓储层决定省略该参数，这里不能把它当作「没有分类」而拒绝加载。
     */
    private fun filterKey(): String {
        val s = _state.value
        val parent = s.parent ?: return ""
        return s.sub?.let { "${parent.slug}_${it.slug}" } ?: parent.slug
    }

    fun loadList() {
        if (_state.value.parent == null) return
        val key = filterKey()
        page = 1
        _state.update { it.copy(loadingList = true, listError = null) }
        viewModelScope.launch {
            val result = runCatching {
                repo.categoryFilter(key, page = 1, order = _state.value.sort.key)
            }
            _state.update {
                it.copy(
                    loadingList = false,
                    comics = result.getOrNull()?.items.orEmpty(),
                    total = result.getOrNull()?.total ?: 0,
                    listError = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.parent == null || s.loadingList || s.loadingMore || s.comics.isEmpty()) return
        val key = filterKey()
        if (s.total > 0 && s.comics.size >= s.total) return

        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val next = page + 1
            val result = runCatching {
                repo.categoryFilter(key, page = next, order = _state.value.sort.key)
            }
            _state.update { prev ->
                val more = result.getOrNull()?.items.orEmpty()
                if (result.isSuccess && more.isNotEmpty()) page = next
                prev.copy(
                    loadingMore = false,
                    comics = if (result.isSuccess) prev.comics + more else prev.comics,
                    total = result.getOrNull()?.total ?: prev.total,
                )
            }
        }
    }
}

/**
 * 分类浏览。
 *
 * 三层结构：父分类 → 子分类 → 排序，下面是结果网格。
 * 层级取自 `categories` 接口，结果来自 `categories/filter`。
 *
 * 若分类树拿不到，退回「热门标签」网格（点标签跳搜索）—— 这是改造前的行为，
 * 保留它可以让接口异常时这一屏仍然可用，而不是只剩一个错误提示。
 */
@Composable
fun CategoryScreen(
    onOpenTag: (String) -> Unit,
    onOpenComic: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = LocalRepository.current
    val vm: CategoryViewModel = viewModel(
        factory = viewModelFactory { initializer { CategoryViewModel(repo) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val c = JmTheme.colors

    Column(modifier = modifier.fillMaxSize()) {
        GlassTopBar(
            title = "分类",
            subtitle = state.parent?.name?.takeIf { it.isNotBlank() } ?: "按分类浏览",
        )

        when {
            state.loading -> LoadingBox()

            state.categories.isEmpty() -> TagFallback(
                tags = state.fallbackTags,
                error = state.error,
                onRetry = { vm.loadTree() },
                onOpenTag = onOpenTag,
            )

            else -> {
                val parent = state.parent
                CategoryChipRow(
                    label = "分类",
                    options = state.categories.map { it.slug to (it.name ?: it.slug) },
                    selected = parent?.slug.orEmpty(),
                    onSelect = { slug ->
                        state.categories.find { it.slug == slug }?.let { vm.selectParent(it) }
                    },
                )

                val subs = parent?.subCategories.orEmpty()
                if (subs.isNotEmpty()) {
                    CategoryChipRow(
                        label = "子类",
                        options = listOf("" to "全部") + subs.map { it.slug to (it.name ?: it.slug) },
                        selected = state.sub?.slug.orEmpty(),
                        onSelect = { slug ->
                            vm.selectSub(if (slug.isEmpty()) null else subs.find { it.slug == slug })
                        },
                    )
                }

                CategoryChipRow(
                    label = "排序",
                    options = CategorySort.entries.map { it.key to it.label },
                    selected = state.sort.key,
                    onSelect = { key ->
                        CategorySort.entries.find { it.key == key }?.let { vm.setSort(it) }
                    },
                )

                val listError = state.listError
                when {
                    state.loadingList -> LoadingBox()

                    listError != null -> ErrorBox(
                        message = listError,
                        onRetry = { vm.loadList() },
                    )

                    state.comics.isEmpty() -> MessageState(
                        title = "这个分类下暂时没有作品",
                        icon = Icons.Filled.Sell,
                        onRetry = { vm.loadList() },
                    )

                    else -> CategoryGrid(
                        comics = state.comics,
                        repo = repo,
                        blocks = state.blocks,
                        loadingMore = state.loadingMore,
                        onOpenComic = onOpenComic,
                        onOpenTag = onOpenTag,
                        onLoadMore = { vm.loadMore() },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChipRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val c = JmTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = c.textTertiary,
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.sm),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            items(options) { (key, text) ->
                FilterChip(
                    selected = selected == key,
                    onClick = { if (selected != key) onSelect(key) },
                    label = { Text(text, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}

/**
 * 结果网格。
 *
 * 触底加载用一个铺满整行的页脚项实现 —— LazyVerticalGrid 只组合可见项，
 * 因此「页脚可见」等价于「已滑到底」，不需要额外监听滚动位置。
 */
@Composable
private fun CategoryGrid(
    comics: List<ListItem>,
    repo: JmRepository,
    blocks: List<com.jmcomic_next.lyqs.data.remote.dto.CategoryBlock>,
    loadingMore: Boolean,
    onOpenComic: (String) -> Unit,
    onOpenTag: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val atBottom by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gridState.layoutInfo.totalItemsCount - 2
        }
    }
    LaunchedEffect(atBottom, comics.size) {
        if (atBottom && comics.isNotEmpty()) onLoadMore()
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(comics, key = { it.id }) { comic ->
            ComicCard(
                item = comic,
                coverUrl = repo.coverUrl(comic),
                onClick = { onOpenComic(comic.id) },
                width = 104.dp,
            )
        }

        // 标签组铺满整行放在结果之后：它是「换个方式浏览」的出口，
        // 不该占据结果上方的位置，也不该与结果抢列宽
        if (blocks.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                TagBlocks(blocks = blocks, onOpenTag = onOpenTag)
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (loadingMore) {
                    CircularProgressIndicator(
                        color = JmTheme.colors.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text(
                        text = "上滑加载更多",
                        style = MaterialTheme.typography.labelSmall,
                        color = JmTheme.colors.textTertiary,
                    )
                }
            }
        }
    }
}

/**
 * 分组标签。
 *
 * 用 [FlowRow] 让标签自然换行 —— 标签长度差异很大，固定列数的网格会出现
 * 「长标签被截断、短标签留一大片空白」的问题。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagBlocks(
    blocks: List<com.jmcomic_next.lyqs.data.remote.dto.CategoryBlock>,
    onOpenTag: (String) -> Unit,
) {
    val c = JmTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        blocks.forEach { block ->
            if (block.content.isEmpty()) return@forEach
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = block.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = c.text,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    block.content.forEach { tag ->
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.xs),
                            color = c.accentSoft,
                            onClick = { onOpenTag(tag) },
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = c.accent,
                                modifier = Modifier.padding(
                                    horizontal = Spacing.sm,
                                    vertical = Spacing.xs,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 分类树拿不到时的兜底：热门标签网格，点标签跳搜索。 */
@Composable
private fun TagFallback(
    tags: List<String>,
    error: String?,
    onRetry: () -> Unit,
    onOpenTag: (String) -> Unit,
) {
    val c = JmTheme.colors
    when {
        error != null && tags.isEmpty() -> ErrorBox(message = error, onRetry = onRetry)

        tags.isEmpty() -> MessageState(
            title = "暂时拿不到分类",
            icon = Icons.Filled.Sell,
            onRetry = onRetry,
        )

        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(tags, key = { it }) { tag ->
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    level = GlassLevel.Card,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.md),
                    onClick = { onOpenTag(tag) },
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.text,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}
