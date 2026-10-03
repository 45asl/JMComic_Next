package com.jmcomic_next.lyqs.ui.screens.search

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jmcomic_next.lyqs.ui.ComicTarget
import com.jmcomic_next.lyqs.ui.jmComicSharedKey
import com.jmcomic_next.lyqs.ui.LocalBottomBarInset
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.prefs.AppPrefs
import com.jmcomic_next.lyqs.data.remote.dto.ListItem
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.components.ComicCard
import com.jmcomic_next.lyqs.ui.components.ComicRow
import com.jmcomic_next.lyqs.ui.components.ErrorBox
import com.jmcomic_next.lyqs.ui.components.GlassTopBar
import com.jmcomic_next.lyqs.ui.components.LoadMoreFooter
import com.jmcomic_next.lyqs.ui.components.LoadingBox
import com.jmcomic_next.lyqs.ui.components.MessageState
import com.jmcomic_next.lyqs.ui.theme.jmShape
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Radius
import com.jmcomic_next.lyqs.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val loading: Boolean = false,
    val error: String? = null,
    val results: List<ListItem> = emptyList(),
    /** 是否已发起过至少一次搜索 —— 用来区分「还没搜」和「搜了没结果」。 */
    val searched: Boolean = false,
    /** 非空表示命中「按编号精确检索」，界面应直接打开该作品。 */
    val redirectAid: String? = null,
    /** 热门标签，未输入关键词时作为检索起点。 */
    val hotTags: List<String> = emptyList(),
    /** 本地搜索历史，最近的在前。 */
    val history: List<String> = emptyList(),
    /** 结果总数（服务端以字符串下发）。0 表示服务端没给。 */
    val total: Int = 0,
    /**
     * 被屏蔽规则滤掉、因而**没有显示**的条数（累计）。
     *
     * 必须显示出来：否则「共 1458 条结果」配上一屏不到十条、翻两页就到底，
     * 看起来就是分页坏了。数字对不上时要能说清是屏蔽吃掉的。
     */
    val hidden: Int = 0,
    val loadingMore: Boolean = false,
    /** 续加失败的原因。与 [error] 分开：失败若写进 [error]，页脚会反复自动重试。 */
    val loadMoreError: String? = null,
    /** 已经到底。 */
    val exhausted: Boolean = false,
    /** 空关键词被提交时给一句提示，而不是什么都不做。 */
    val hint: String? = null,
    /**
     * 随机推荐。
     *
     * 官方把它放在搜索页「还没开始搜」的时候（`Search.tsx` 的 `FETCH_RECOMMEND_THUNK`），
     * 作用是给一个**不用想关键词**的入口 —— 空着的一屏比一屏推荐更让人无从下手。
     */
    val recommend: List<ListItem> = emptyList(),
)

class SearchViewModel(
    private val repo: JmRepository,
    private val prefs: AppPrefs,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState(history = prefs.searchHistory))
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /** 搜索每页 80 条，与官方一致（`Math.ceil(total / 80)`）。 */
    private val pageSize = 80
    private var page = 1

    /**
     * 每次「重新搜索」自增的世代号。
     *
     * 改筛选项/换关键词时会从第一页重来，而此时可能还有一个在飞的「加载更多」；
     * 它回来时若照旧并进列表，就会把**上一个条件的**结果混进新结果里 ——
     * 界面表现为「列表前后内容对不上」，是用户最难描述、也最难复现的一类错误。
     */
    private var generation = 0

    init {
        loadHotTags()
        loadRecommend()
    }

    /** 热门标签失败不影响搜索本身，因此只静默留空。 */
    private fun loadHotTags() {
        viewModelScope.launch {
            val tags = runCatching {
                repo.bootstrap()
                repo.hotTags()
            }.getOrDefault(emptyList())
            _state.update { it.copy(hotTags = tags) }
        }
    }

    /** 随机推荐同样只影响「没搜索时」那一屏，失败就留空。 */
    private fun loadRecommend() {
        viewModelScope.launch {
            val items = runCatching {
                repo.bootstrap()
                repo.randomRecommend()
            }.getOrDefault(emptyList())
            _state.update { it.copy(recommend = items) }
        }
    }

    fun clearHistory() {
        prefs.clearSearchHistory()
        _state.update { it.copy(history = emptyList()) }
    }

    fun onQueryChange(q: String) = _state.update { it.copy(query = q) }

    /** 跳转已被消费，清掉以免返回时反复触发。 */
    fun consumeRedirect() = _state.update { it.copy(redirectAid = null) }

    /** 改动任一筛选项都立刻重搜 —— 结果已经不在屏幕上时，等用户再点一次没有意义。 */
    fun updateFilters(transform: (SearchFilters) -> SearchFilters) {
        _state.update { it.copy(filters = transform(it.filters)) }
        search()
    }

    fun search() {
        val s = _state.value
        val q = s.query.trim()
        if (s.loading) return
        if (q.isEmpty()) {
            // 点搜索图标而输入框是空的：什么都不发生会让人以为按钮坏了
            _state.update { it.copy(hint = "请输入关键词") }
            return
        }
        val f = s.filters

        // 记历史放在发起请求之前：用户按下搜索就代表这次检索意图成立，
        // 哪怕请求失败，这个词也仍然是他想搜的
        prefs.addSearchHistory(q)
        generation++
        page = 1
        _state.update {
            it.copy(
                loading = true,
                loadingMore = false,
                error = null,
                hint = null,
                loadMoreError = null,
                exhausted = false,
                history = prefs.searchHistory,
            )
        }

        viewModelScope.launch {
            val result = runCatching {
                repo.bootstrap()
                repo.search(
                    query = q,
                    page = 1,
                    order = f.order,
                    type = f.type,
                    year = f.year.takeIf { it.isNotEmpty() },
                    month = f.month.takeIf { it.isNotEmpty() },
                )
            }
            _state.update { prev ->
                var items = result.getOrNull()?.page?.items.orEmpty()
                // 「最旧」这一档官方客户端会在本地按 adddate 二次排序，照做
                if (f.isLocalOldest) {
                    items = items.sortedBy { it.addDate.orEmpty() }
                }
                prev.copy(
                    loading = false,
                    searched = true,
                    results = items,
                    total = result.getOrNull()?.page?.total ?: 0,
                    hidden = result.getOrNull()?.page?.hidden ?: 0,
                    redirectAid = result.getOrNull()?.redirectAid,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    /**
     * 加载下一页。
     *
     * 带筛选条件一起请求：筛选改动会重置到第一页，若加载更多时用了旧的筛选，
     * 会把两组不同条件的结果拼在一起 —— 这种错误在界面上表现为「列表内容前后不一致」，
     * 很难被用户描述清楚，所以一开始就不能让它发生。
     */
    fun loadMore() {
        val s = _state.value
        val q = s.query.trim()
        if (q.isEmpty() || s.loading || s.loadingMore) return
        if (s.results.isEmpty() || s.redirectAid != null) return
        if (s.exhausted || s.loadMoreError != null) return
        if (s.total > 0 && s.results.size >= s.total) {
            _state.update { it.copy(exhausted = true) }
            return
        }

        val f = s.filters
        val gen = generation
        _state.update { it.copy(loadingMore = true, loadMoreError = null) }
        viewModelScope.launch {
            val next = page + 1
            val result = runCatching {
                repo.search(
                    query = q,
                    page = next,
                    order = f.order,
                    type = f.type,
                    year = f.year.takeIf { it.isNotEmpty() },
                    month = f.month.takeIf { it.isNotEmpty() },
                )
            }
            // 条件已变：这次的结果属于上一轮搜索，直接丢弃。
            // 但「正在续加」的标记必须落下 —— 否则它会一直挂着，新条件再也加载不了下一页
            if (gen != generation) {
                _state.update { it.copy(loadingMore = false) }
                return@launch
            }
            _state.update { prev ->
                var more = result.getOrNull()?.page?.items.orEmpty()
                val ok = result.isSuccess
                if (ok && more.isNotEmpty()) page = next
                var merged = if (ok) prev.results + more else prev.results
                if (f.isLocalOldest && ok) {
                    merged = merged.sortedBy { it.addDate.orEmpty() }
                }
                prev.copy(
                    loadingMore = false,
                    results = merged,
                    total = result.getOrNull()?.page?.total ?: prev.total,
                    // 累计：这一页被滤掉几条，之前几页也要算上
                    hidden = prev.hidden + if (ok) result.getOrNull()?.page?.hidden ?: 0 else 0,
                    loadMoreError = if (ok) null else result.exceptionOrNull()?.message,
                    // 成功但本页为空 = 到底了
                    exhausted = ok && more.isEmpty(),
                )
            }
        }
    }

    /** 续加失败后的重试：先清错误，否则 [loadMore] 会立刻早退。 */
    fun retryLoadMore() {
        _state.update { it.copy(loadMoreError = null) }
        loadMore()
    }
}

/**
 * 搜索页。
 *
 * 筛选条件是可用的：排序（`o`）与检索字段（`search_type`）各一行，年份/月份收在
 * 「更多筛选」里 —— 年份有十来个取值，默认铺开会把结果挤到屏幕外。
 */
@Composable
fun SearchScreen(
    onOpenComic: (ComicTarget) -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
) {
    val repo = LocalRepository.current
    val context = LocalContext.current
    val prefs = remember(context) { AppPrefs(context) }
    val vm: SearchViewModel = viewModel(
        factory = viewModelFactory { initializer { SearchViewModel(repo, prefs) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf(initialQuery) }
    var showDateFilter by rememberSaveable { mutableStateOf(false) }
    /**
     * 带标签进来时自动搜过没有。
     *
     * 只用 `LaunchedEffect(initialQuery)` 是不够的：参数在同一个返回栈条目上永不变化，
     * 但**每次重新进入组合都会再跑一次**（从详情页返回就是这种情况）。
     * 那会拿最初的标签覆盖用户后来自己输入的关键词，于是输入框显示的是新词、
     * 列表显示的是旧标签的结果 —— 而且用户完全看不出为什么。
     */
    var autoSearched by rememberSaveable { mutableStateOf(false) }
    val c = JmTheme.colors

    // 命中「按编号精确检索」时直接打开作品，不展示列表
    LaunchedEffect(state.redirectAid) {
        state.redirectAid?.let { id ->
            vm.consumeRedirect()
            // 「按编号精确检索」只有编号：封面退回按 id 拼模板，标题等接口返回
            onOpenComic(ComicTarget(id))
        }
    }

    // 从分类页带着标签进来时直接开搜，省掉一次手动确认（只做一次，见 autoSearched）
    LaunchedEffect(initialQuery, autoSearched) {
        if (!autoSearched && initialQuery.isNotBlank()) {
            autoSearched = true
            vm.onQueryChange(initialQuery)
            vm.search()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        GlassTopBar(title = "搜索")

        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                vm.onQueryChange(it)
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            placeholder = { Text("输入作品名、作者或标签", color = c.textTertiary) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { vm.search() }) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索", tint = c.accent)
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search() }),
        )

        state.hint?.let { hint ->
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = c.accent,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }

        FilterRow(
            label = "排序",
            options = SearchFilters.Order.entries.map { it.key to it.label },
            selected = state.filters.order,
            enabled = !state.loading,
            onSelect = { key -> vm.updateFilters { it.copy(order = key) } },
        )

        FilterRow(
            label = "检索",
            options = SearchFilters.Type.entries.map { it.key to it.label },
            selected = state.filters.type,
            enabled = !state.loading,
            onSelect = { key -> vm.updateFilters { it.copy(type = key) } },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (state.filters.year.isEmpty()) "年份：不限" else
                    "年份：${state.filters.year}${if (state.filters.month.isEmpty()) " 年" else " 年 ${state.filters.month} 月"}",
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showDateFilter = !showDateFilter }) {
                Icon(
                    imageVector = if (showDateFilter) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (showDateFilter) "收起年份筛选" else "展开年份筛选",
                    tint = c.accent,
                )
            }
        }

        if (showDateFilter) {
            DateFilterRows(
                filters = state.filters,
                enabled = !state.loading,
                onChange = { year, month -> vm.updateFilters { it.copy(year = year, month = month) } },
            )
        }

        when {
            state.loading -> LoadingBox()

            state.error != null && state.results.isEmpty() ->
                ErrorBox(message = state.error.orEmpty(), onRetry = { vm.search() })

            !state.searched -> SuggestionPanel(
                history = state.history,
                hotTags = state.hotTags,
                recommend = state.recommend,
                coverUrl = { repo.coverUrl(it) },
                onOpenComic = onOpenComic,
                onPick = { word ->
                    input = word
                    vm.onQueryChange(word)
                    vm.search()
                },
                onClearHistory = { vm.clearHistory() },
            )

            state.results.isEmpty() -> MessageState(
                title = "没有找到相关作品",
                description = "换个关键词，或调整检索字段与年份",
                icon = Icons.Filled.Search,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    // 悬浮底栏会盖住列表底部
                    bottom = Spacing.xxl + LocalBottomBarInset.current,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (state.total > 0) {
                    item(key = "count") {
                        Text(
                            text = buildString {
                                append("共 ${state.total} 条结果")
                                if (state.hidden > 0) append(" · 已按屏蔽规则隐藏 ${state.hidden} 条")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textTertiary,
                        )
                    }
                }
                items(state.results, key = { it.id }) { comic ->
                    val cover = repo.coverUrl(comic)
                    Box(Modifier.fillMaxWidth()) {
                        ComicRow(
                            item = comic,
                            coverUrl = cover,
                            onClick = {
                                onOpenComic(ComicTarget(comic.id, cover, comic.name.orEmpty()))
                            },
                        )
                    }
                }
                item(key = "footer") {
                    LoadMoreFooter(
                        loading = state.loadingMore,
                        error = state.loadMoreError,
                        exhausted = state.exhausted,
                        onLoadMore = { vm.loadMore() },
                        onRetry = { vm.retryLoadMore() },
                    )
                }
            }
        }
    }
}

/**
 * 未搜索时的建议面板：最近搜过的词 + 热门标签。
 *
 * 两者都是「点一下就能开始检索」的入口，因此视觉上同构（同一套 chip），
 * 只在标题上区分。热门的顺序由服务端给，不再自行排序。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionPanel(
    history: List<String>,
    hotTags: List<String>,
    recommend: List<ListItem>,
    coverUrl: (ListItem) -> String,
    onOpenComic: (ComicTarget) -> Unit,
    onPick: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    val c = JmTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            // 同上：让悬浮胶囊有可滚出去的空间
            .padding(bottom = LocalBottomBarInset.current),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        if (history.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "最近搜索",
                        style = MaterialTheme.typography.titleMedium,
                        color = c.text,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearHistory) {
                        Text("清空", color = c.textSecondary)
                    }
                }
                WordChips(history, onPick)
            }
        }

        if (hotTags.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = "热门标签",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.text,
                )
                WordChips(hotTags, onPick)
            }
        }

        // 随机推荐放在最下面：它是「没有想法时的电梯」，不该把历史与标签挤下去。
        // 一屏里也能顺手滑到，所以不妨碍常规路径。
        if (recommend.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = "随机推荐",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.text,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    items(recommend, key = { it.id }) { comic ->
                        val cover = coverUrl(comic)
                        ComicCard(
                            item = comic,
                            coverUrl = cover,
                            onClick = {
                                onOpenComic(ComicTarget(comic.id, cover, comic.name.orEmpty()))
                            },
                            sharedKey = jmComicSharedKey(comic.id),
                        )
                    }
                }
            }
        }

        if (history.isEmpty() && hotTags.isEmpty() && recommend.isEmpty()) {
            MessageState(
                title = "搜点什么",
                description = "支持按作品名、作者、标签检索",
                icon = Icons.Filled.Search,
            )
        }
    }
}

/** 一组可点的检索词。用 FlowRow 自然换行：词长差异大，固定列会浪费或截断。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordChips(words: List<String>, onPick: (String) -> Unit) {
    val c = JmTheme.colors
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        words.forEach { word ->
            Surface(
                shape = jmShape(Radius.xs),
                color = c.accentSoft,
                onClick = { onPick(word) },
            ) {
                Text(
                    text = word,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.accent,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
        }
    }
}

/** 一行筛选 chip。选中项用强调色，符合本套设计的强调方式。 */
@Composable
private fun FilterRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    enabled: Boolean,
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
                    enabled = enabled,
                    label = {
                        Text(text, style = MaterialTheme.typography.labelSmall)
                    },
                )
            }
        }
    }
}

/**
 * 年份与月份筛选。
 *
 * 年份范围与官方一致：2017 起至今（官方是 `currentYear - 2017 + 1` 个选项）。
 * 月份只在选了年份之后才有意义，因此未选年份时不展示。
 */
@Composable
private fun DateFilterRows(
    filters: SearchFilters,
    enabled: Boolean,
    onChange: (year: String, month: String) -> Unit,
) {
    val currentYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    val years = remember(currentYear) { (currentYear downTo 2017).map { it.toString() } }
    val months = remember { (1..12).map { it.toString() } }

    FilterRow(
        label = "年",
        options = listOf("" to "不限") + years.map { it to it },
        selected = filters.year,
        enabled = enabled,
        onSelect = { y -> onChange(y, if (y.isEmpty()) "" else filters.month) },
    )

    if (filters.year.isNotEmpty()) {
        FilterRow(
            label = "月",
            options = listOf("" to "不限") + months.map { it to it },
            selected = filters.month,
            enabled = enabled,
            onSelect = { m -> onChange(filters.year, m) },
        )
    }
}
