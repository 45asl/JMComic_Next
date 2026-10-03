package com.jmcomic_next.lyqs.ui.screens.search

import com.jmcomic_next.lyqs.data.prefs.SharedPrefsKeyValueStore
import com.jmcomic_next.lyqs.ui.components.jmAnimateItem
import com.jmcomic_next.lyqs.ui.LocalTagBlocker
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
import androidx.compose.material.icons.filled.Block
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.jmcomic_next.lyqs.ui.components.GlassLevel
import com.jmcomic_next.lyqs.ui.components.GlassSurface
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
    /**
     * 每次「重新搜索」自增的编号（1.5.2）。
     *
     * 与 ViewModel 内部那个世代号同源，只是也让界面看得见：搜索页要在**新一次搜索**发起时
     * 撤销上一次的「允许一次」（见 `TagBlockResolver.clearAllowances`），
     * 否则上一次点开放行的作品会跟着关键词飘到下一次搜索结果里。
     */
    val searchId: Int = 0,
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

    /** 换一批随机推荐（1.5.5）：重新拉一次，失败保持原样。 */
    fun shuffleRecommend() = loadRecommend()

    /**
     * 随手抽一本（1.5.5）。
     *
     * **重新拉一次再抽**，而不是从当前这一屏里挑：那一屏只有十几条，
     * 反复点会一直看到同样几本，和"随机"的预期不符。
     * 抽不到时给一句提示，而不是静默什么都不做 —— 用户会以为按钮坏了。
     */
    fun openRandomOne(onOpen: (String) -> Unit) {
        viewModelScope.launch {
            val item = runCatching {
                repo.bootstrap()
                repo.randomRecommend()
            }.getOrDefault(emptyList()).randomOrNull()
            if (item == null) {
                _state.update { it.copy(hint = "随机本子没拿到，稍后再试") }
            } else {
                onOpen(item.id)
            }
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
                // 界面据此撤销上一次的「允许一次」：新的检索条件该重新按规则判
                searchId = generation,
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
    val prefs = remember(context) { AppPrefs(SharedPrefsKeyValueStore(context, "jm_prefs")) }
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

    // 标签屏蔽（1.5.2）：搜索结果同样按标签规则过滤，而且**要能说清是谁挡的**。
    // 没有解析器（或没有标签规则）时 `hidden` 恒为空集合，列表与提示条都不出现。
    val tagBlocker = LocalTagBlocker.current
    val hiddenIds by remember(tagBlocker) {
        tagBlocker?.hidden ?: MutableStateFlow(emptySet<String>())
    }.collectAsStateWithLifecycle()
    val blockedBy by remember(tagBlocker) {
        tagBlocker?.blockedBy ?: MutableStateFlow(emptyMap<String, Set<String>>())
    }.collectAsStateWithLifecycle()

    // 「允许一次」只活这一次搜索：发起新一次检索时撤销上一次的放行。
    // 用 rememberSaveable 记住已处理的编号 —— 从详情页返回会重新进入组合、
    // LaunchedEffect 会重跑，但同一次搜索不该再撤销一次（否则刚放行的作品又被藏回去）。
    var lastSearchId by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(state.searchId) {
        if (state.searchId > 0 && state.searchId != lastSearchId) {
            lastSearchId = state.searchId
            tagBlocker?.clearAllowances()
        }
    }

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

        // 本次结果里被标签规则挡掉的作品。**只算这次搜索真的返回了的那些**：
        // 解析器里的 blockedBy 是全 App 共享的缓存，别的页面藏掉的作品
        // 不该出现在搜索页这条提示里（否则标签列了一堆，点「允许一次」却什么都不变）。
        val tagBlocked = state.results.filter { it.id in hiddenIds }
        val tagBlockedTags = tagBlocked.flatMap { blockedBy[it.id].orEmpty() }.distinct()

        // 提示条放在列表**外面**（固定在列表上方），两个原因：
        //  1. 它是「这次搜索有东西被挡掉」的说明，跟着列表滚走就没人看见；
        //  2. 放进 LazyColumn 首位会踩到 LazyList 的按 key 锚定：插入时它会把插入前
        //     的首个可见项钉在原位（`animateItem` 依赖的正是这个行为），于是新插入的
        //     提示条被顶到可视区之外 —— 实测「加完标签规则后提示条根本看不见，
        //     要手动往上滑才出来」。移出列表就没有这个位置竞争。
        if (tagBlocked.isNotEmpty() && !state.loading) {
            TagBlockedNotice(
                count = tagBlocked.size,
                tags = tagBlockedTags,
                onAllowOnce = { tagBlocker?.allowOnce(tagBlocked.map { it.id }.toSet()) },
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
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
                onShuffle = { vm.shuffleRecommend() },
                onRandomOne = {
                    // 封面与标题这里没有，交给详情页自己拉 —— 传空串即可，
                    // 代价是这一次没有共享元素动画（跳转仍然完整可用）
                    vm.openRandomOne { id -> onOpenComic(ComicTarget(id, "", "")) }
                },
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
                // 「有结果被你的屏蔽规则挡掉了」的说明条已移到列表外（见上面）：
                // 放进列表首位会被 LazyList 的锚定顶出可视区
                if (state.total > 0) {
                    item(key = "count") {
                        Text(
                            text = buildString {
                                append("共 ${state.total} 条结果")
                                if (state.hidden > 0) append(" · 已按屏蔽规则隐藏 ${state.hidden} 条")
                                // 标签屏蔽是本地异步补上的，与上面服务端/关键词那部分分开算
                                if (tagBlocked.isNotEmpty()) {
                                    append(" · 标签屏蔽 ${tagBlocked.size} 条")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textTertiary,
                        )
                    }
                }
                // 命中标签规则的作品滤掉（标签是异步取回来的，所以列表会随结果收敛）
                items(state.results.filterNot { it.id in hiddenIds }, key = { it.id }) { comic ->
                    // 条目可见才去取详情拿标签；没有标签规则时 request 内部直接返回，不发任何请求
                    LaunchedEffect(comic.id) { tagBlocker?.request(comic.id) }
                    val cover = repo.coverUrl(comic)
                    Box(Modifier.fillMaxWidth().jmAnimateItem(this)) {
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
 * 「有结果被标签屏蔽挡住了」提示条（1.5.2）。
 *
 * 为什么不能只显示一个数字：搜索页最容易出现的误解是「搜不到」——
 * 用户看到结果少、翻两下就到底，会以为关键词不对，然后换词、再换词。
 * 所以这里直接说清三件事：**有结果**、是**你的标签规则**挡的、
 * 挡人的**标签是哪些**，并给一个马上能看的出口。
 *
 * 样式沿用详情页那条「按你的屏蔽规则…」提示（[GlassSurface] + 图标 + 右侧文字按钮），
 * 不再另造一套视觉。
 *
 * 「允许一次」只作用于**本次搜索**、不写回屏蔽规则：按钮文案明确写「一次」，
 * 副标题也把「不改动屏蔽规则」说出来，避免用户以为自己把规则改掉了。
 */
@Composable
private fun TagBlockedNotice(
    count: Int,
    tags: List<String>,
    onAllowOnce: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = JmTheme.colors
    GlassSurface(modifier = modifier.fillMaxWidth(), level = GlassLevel.Card) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Block,
                contentDescription = null,
                tint = c.textTertiary,
                modifier = Modifier.size(18.dp),
            )
            Column(
                modifier = Modifier.weight(1f).padding(start = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(
                    text = "有 $count 条结果被标签屏蔽挡住了",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.text,
                )
                if (tags.isNotEmpty()) {
                    Text(
                        // 「命中标签」用顿号连接：标签里出现逗号是常事，再用逗号分隔会读不清
                        text = "命中：${tags.joinToString("、")}（仅本次搜索放行，不改动屏蔽规则）",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textSecondary,
                    )
                }
            }
            TextButton(onClick = onAllowOnce) {
                Text("允许一次", color = c.accent)
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
    /** 换一批随机推荐（1.5.5）。 */
    onShuffle: () -> Unit,
    /** 随手抽一本：直接进那本的详情页（1.5.5）。 */
    onRandomOne: () -> Unit,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "随机推荐",
                        style = MaterialTheme.typography.titleMedium,
                        color = c.text,
                        modifier = Modifier.weight(1f),
                    )
                    // 「随便来一本」是这一行真正想要的用法：不用挑，直接看
                    TextButton(onClick = onRandomOne) { Text("随便来一本") }
                    TextButton(onClick = onShuffle) { Text("换一批") }
                }
                // 标签屏蔽（1.5.1）：命中集合是异步补上来的（没有标签规则时恒为空）
                val tagBlocker = LocalTagBlocker.current
                val hiddenIds by remember(tagBlocker) {
                    tagBlocker?.hidden ?: MutableStateFlow(emptySet<String>())
                }.collectAsStateWithLifecycle()
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    items(recommend.filterNot { it.id in hiddenIds }, key = { it.id }) { comic ->
                        // 条目可见才去取详情拿标签（没有标签规则时不发请求）
                        LaunchedEffect(comic.id) { tagBlocker?.request(comic.id) }
                        val cover = coverUrl(comic)
                        ComicCard(
                            item = comic,
                            coverUrl = cover,
                            onClick = {
                                onOpenComic(ComicTarget(comic.id, cover, comic.name.orEmpty()))
                            },
                            sharedKey = jmComicSharedKey(comic.id),
                            modifier = Modifier.jmAnimateItem(this),
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
