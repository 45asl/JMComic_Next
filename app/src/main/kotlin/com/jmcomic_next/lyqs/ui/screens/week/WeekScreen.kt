package com.jmcomic_next.lyqs.ui.screens.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jmcomic_next.lyqs.ui.jmComicSharedKey
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.remote.dto.ListItem
import com.jmcomic_next.lyqs.data.remote.dto.WeekCategory
import com.jmcomic_next.lyqs.data.remote.dto.WeekType
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.components.CardSizes
import com.jmcomic_next.lyqs.ui.components.ComicCard
import com.jmcomic_next.lyqs.ui.components.ErrorBox
import com.jmcomic_next.lyqs.ui.components.GlassTopBar
import com.jmcomic_next.lyqs.ui.components.LoadMoreFooter
import com.jmcomic_next.lyqs.ui.components.LoadingBox
import com.jmcomic_next.lyqs.ui.components.MessageState
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Spacing
import com.jmcomic_next.lyqs.ui.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeekUiState(
    val loading: Boolean = true,
    val error: String? = null,
    /** 刊期列表（新→旧）。 */
    val issues: List<WeekCategory> = emptyList(),
    /** 作品类型，取自服务端，客户端不自己编。 */
    val types: List<WeekType> = emptyList(),
    val issue: WeekCategory? = null,
    val type: WeekType? = null,
    val items: List<ListItem> = emptyList(),
    val total: Int = 0,
    val loadingList: Boolean = false,
    val loadingMore: Boolean = false,
    val loadMoreError: String? = null,
    val exhausted: Boolean = false,
)

/**
 * 期刊（周刊）浏览。
 *
 * 服务端的 `week` 接口先给两个「维度」：**刊期**（`categories`，形如「2026第258期09.25 - 09.18」）
 * 与**作品类型**（`type`：日漫 / 其他 / 韩漫），然后 `week/filter` 按这两个 id 取列表。
 * 也就是说这一屏的正确顺序是「先拿维度，再按默认的第一项去筛」，而不是硬编 id ——
 * 实测刊期 id（259）与期号（第 258 期）并不相等，编不出来。
 */
class WeekViewModel(private val repo: JmRepository) : ViewModel() {

    private val _state = MutableStateFlow(WeekUiState())
    val state: StateFlow<WeekUiState> = _state.asStateFlow()

    /** `week/filter` 的页码从 1 起算。 */
    private var page = 1

    /** 换刊期/换类型后自增，用来丢弃过期的「加载更多」结果。 */
    private var generation = 0

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = runCatching {
                repo.bootstrap()
                repo.weekIssues()
            }
            val payload = result.getOrNull()
            val issue = payload?.categories?.firstOrNull()
            val type = payload?.type?.firstOrNull()
            _state.update {
                it.copy(
                    loading = false,
                    issues = payload?.categories.orEmpty(),
                    types = payload?.type.orEmpty(),
                    issue = issue,
                    type = type,
                    error = result.exceptionOrNull().toUserMessage(),
                )
            }
            if (issue != null && type != null) loadList()
        }
    }

    fun selectIssue(next: WeekCategory) {
        if (_state.value.issue?.id == next.id) return
        _state.update { it.copy(issue = next) }
        loadList()
    }

    fun selectType(next: WeekType) {
        if (_state.value.type?.id == next.id) return
        _state.update { it.copy(type = next) }
        loadList()
    }

    fun loadList() {
        val s = _state.value
        val issue = s.issue ?: return
        val type = s.type ?: return
        generation++
        val gen = generation
        page = 1
        _state.update {
            it.copy(
                loadingList = true,
                loadMoreError = null,
                loadingMore = false,
                exhausted = false,
            )
        }
        viewModelScope.launch {
            val result = runCatching { repo.weekList(issue.id, type.id, page = 1) }
            if (gen != generation) return@launch
            _state.update {
                it.copy(
                    loadingList = false,
                    items = result.getOrNull()?.items.orEmpty(),
                    total = result.getOrNull()?.total ?: 0,
                    loadMoreError = if (result.isSuccess) null else result.exceptionOrNull().toUserMessage(),
                )
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        val issue = s.issue ?: return
        val type = s.type ?: return
        if (s.loadingList || s.loadingMore || s.items.isEmpty()) return
        if (s.exhausted || s.loadMoreError != null) return
        if (s.total > 0 && s.items.size >= s.total) {
            _state.update { it.copy(exhausted = true) }
            return
        }

        val gen = generation
        _state.update { it.copy(loadingMore = true, loadMoreError = null) }
        viewModelScope.launch {
            val next = page + 1
            val result = runCatching { repo.weekList(issue.id, type.id, page = next) }
            // 换过刊期/类型：这一页属于上一个条件，丢弃；标记照例落下
            if (gen != generation) {
                _state.update { it.copy(loadingMore = false) }
                return@launch
            }
            _state.update { prev ->
                val more = result.getOrNull()?.items.orEmpty()
                if (result.isSuccess && more.isNotEmpty()) page = next
                prev.copy(
                    loadingMore = false,
                    items = if (result.isSuccess) prev.items + more else prev.items,
                    total = result.getOrNull()?.total?.takeIf { t -> t > 0 } ?: prev.total,
                    loadMoreError = if (result.isSuccess) null else result.exceptionOrNull().toUserMessage(),
                    exhausted = result.isSuccess && more.isEmpty(),
                )
            }
        }
    }

    fun retryLoadMore() {
        _state.update { it.copy(loadMoreError = null) }
        loadMore()
    }
}

/**
 * 期刊浏览页。
 *
 * 官方把入口放在顶栏的日历图标与首页的横幅上（`Header.tsx` / `Banner.tsx` → `/week`），
 * 本应用同理：首页顶栏给一个日历按钮。
 */
@Composable
fun WeekScreen(
    onBack: () -> Unit,
    onOpenComic: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = LocalRepository.current
    val vm: WeekViewModel = viewModel(
        factory = viewModelFactory { initializer { WeekViewModel(repo) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val c = JmTheme.colors

    Column(modifier = modifier.fillMaxSize()) {
        GlassTopBar(
            title = "周刊",
            subtitle = state.issue?.let { issue ->
                if (state.total > 0) "${issue.label} · 共 ${state.total} 部" else issue.label
            },
            navigation = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = c.accent,
                    )
                }
            },
        )

        if (state.issues.isNotEmpty()) {
            ChipRow(
                label = "刊期",
                options = state.issues,
                selectedId = state.issue?.id.orEmpty(),
                labelOf = { it.label },
                onSelect = { vm.selectIssue(it) },
            )
        }
        if (state.types.isNotEmpty()) {
            ChipRow(
                label = "类型",
                options = state.types,
                selectedId = state.type?.id.orEmpty(),
                labelOf = { it.label },
                onSelect = { vm.selectType(it) },
            )
        }

        when {
            state.loading -> LoadingBox()

            state.error != null && state.issues.isEmpty() ->
                ErrorBox(message = state.error.orEmpty(), onRetry = { vm.load() })

            state.loadingList -> LoadingBox()

            state.items.isEmpty() -> MessageState(
                title = "这一期没有作品",
                description = "换一个刊期或类型看看",
                icon = Icons.Filled.CalendarMonth,
                onRetry = { vm.loadList() },
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = CardSizes.grid),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(state.items, key = { it.id }) { comic ->
                    ComicCard(
                        item = comic,
                        coverUrl = repo.coverUrl(comic),
                        onClick = { onOpenComic(comic.id) },
                        sharedKey = jmComicSharedKey(comic.id),
                        width = CardSizes.grid,
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
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

/** 一行可横向滚动的选择条（刊期有几十项，必须能滚）。 */
@Composable
private fun <T> ChipRow(
    label: String,
    options: List<T>,
    selectedId: String,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = JmTheme.colors.textTertiary,
                    modifier = Modifier.padding(end = Spacing.sm),
                )
            }
            items(options) { option ->
                val id = when (option) {
                    is WeekCategory -> option.id
                    is WeekType -> option.id
                    else -> labelOf(option)
                }
                FilterChip(
                    selected = selectedId == id,
                    onClick = { onSelect(option) },
                    label = {
                        Text(labelOf(option), style = MaterialTheme.typography.labelSmall)
                    },
                )
            }
        }
    }
}
