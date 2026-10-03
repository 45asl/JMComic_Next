package com.jmcomic_next.lyqs.ui.screens.creator

import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.remote.dto.CreatorAuthor
import com.jmcomic_next.lyqs.data.remote.dto.CreatorWork
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.components.CardSizes
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
import com.jmcomic_next.lyqs.ui.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 创作者库的两个维度。 */
enum class CreatorTab(val label: String) {
    Authors("画师"),
    Works("作品"),
}

data class CreatorUiState(
    val tab: CreatorTab = CreatorTab.Authors,
    val query: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val authors: List<CreatorAuthor> = emptyList(),
    val works: List<CreatorWork> = emptyList(),
    val total: Int = 0,
    val loadingMore: Boolean = false,
    val loadMoreError: String? = null,
    val exhausted: Boolean = false,
    /** 非空表示这一屏在展示「某画师名下的作品」。 */
    val authorId: String? = null,
    val authorName: String? = null,
)

/**
 * 创作者库。
 *
 * 官方把它放在「分类 → 画师库」入口（`Header.tsx` 的 `/library?from=categories`），
 * 两个维度各有**完全不同的字段与封套**：
 *
 *  - 画师：`creator_author` → `{status, data:{total, content:[{id, author_name, author_avatar,…}]}}`
 *  - 作品：`creator_work` → `{status, data:{total, content:[{id, work_title, work_image, platform_name,…}]}}`
 *
 * 外层是统一封包，**解出来的 data 里还有一层** `{status, data:{…}}`，
 * 而且两个接口的 `status` 一个回字符串、一个回数字 —— 所以这一层不拿 status 判断成败，
 * 只看外层封包的 code（见 `JmRepository.toResult`）。
 */
class CreatorViewModel(
    private val repo: JmRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreatorUiState())
    val state: StateFlow<CreatorUiState> = _state.asStateFlow()

    private var page = 1

    init {
        load()
    }

    fun setTab(tab: CreatorTab) {
        if (_state.value.tab == tab || _state.value.authorId != null) return
        _state.update { it.copy(tab = tab) }
        load()
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    /** 进入某画师的作品列表。 */
    fun openAuthor(author: CreatorAuthor) {
        _state.update {
            it.copy(authorId = author.id, authorName = author.name, tab = CreatorTab.Works)
        }
        load()
    }

    /** 返回上一层（画师列表）。 */
    fun closeAuthor() {
        if (_state.value.authorId == null) return
        _state.update { it.copy(authorId = null, authorName = null, tab = CreatorTab.Authors) }
        load()
    }

    fun load() {
        val s = _state.value
        page = 1
        _state.update {
            it.copy(loading = true, error = null, loadMoreError = null, exhausted = false)
        }
        viewModelScope.launch {
            // 三条取数路径分开写：它们的返回类型不同（画师 / 作品），
            // 合并成一条 when 会让泛型退化成 List<Any>
            val authorId = s.authorId
            runCatching {
                repo.bootstrap()
                when {
                    authorId != null -> {
                        val r = repo.creatorWorksByAuthor(authorId)
                        _state.update {
                            it.copy(
                                loading = false,
                                works = r.items,
                                total = r.total,
                                // 按作者取的这个接口没有分页，直接标到底
                                exhausted = true,
                            )
                        }
                    }

                    s.tab == CreatorTab.Authors -> {
                        val r = repo.creatorAuthors(page = 1, query = s.query)
                        _state.update {
                            it.copy(loading = false, authors = r.items, total = r.total)
                        }
                    }

                    else -> {
                        val r = repo.creatorWorks(page = 1, searchValue = s.query)
                        _state.update {
                            it.copy(loading = false, works = r.items, total = r.total)
                        }
                    }
                }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore) return
        if (s.exhausted || s.loadMoreError != null) return
        if (s.total > 0 && loadedCount() >= s.total) {
            _state.update { it.copy(exhausted = true) }
            return
        }
        if (loadedCount() == 0) return

        _state.update { it.copy(loadingMore = true, loadMoreError = null) }
        viewModelScope.launch {
            val next = page + 1
            // 两个维度的元素类型不同，各自成段处理；合并会让泛型退化成 List<Any>
            if (s.tab == CreatorTab.Authors) {
                val result = runCatching { repo.creatorAuthors(next, s.query) }
                val items = result.getOrNull()?.items.orEmpty()
                _state.update { prev ->
                    if (result.isSuccess && items.isNotEmpty()) page = next
                    prev.copy(
                        loadingMore = false,
                        authors = if (result.isSuccess) prev.authors + items else prev.authors,
                        total = result.getOrNull()?.total?.takeIf { it > 0 } ?: prev.total,
                        loadMoreError = result.exceptionOrNull()?.toUserMessage(),
                        exhausted = result.isSuccess && items.isEmpty(),
                    )
                }
            } else {
                val result = runCatching { repo.creatorWorks(next, s.query) }
                val items = result.getOrNull()?.items.orEmpty()
                _state.update { prev ->
                    if (result.isSuccess && items.isNotEmpty()) page = next
                    prev.copy(
                        loadingMore = false,
                        works = if (result.isSuccess) prev.works + items else prev.works,
                        total = result.getOrNull()?.total?.takeIf { it > 0 } ?: prev.total,
                        loadMoreError = result.exceptionOrNull()?.toUserMessage(),
                        exhausted = result.isSuccess && items.isEmpty(),
                    )
                }
            }
        }
    }

    fun retryLoadMore() {
        _state.update { it.copy(loadMoreError = null) }
        loadMore()
    }

    private fun loadedCount(): Int {
        val s = _state.value
        return if (s.tab == CreatorTab.Authors && s.authorId == null) s.authors.size else s.works.size
    }
}

/**
 * 创作者库：画师 / 作品两个维度，点画师看其名下作品，点作品看作品信息。
 */
@Composable
fun CreatorScreen(
    onBack: () -> Unit,
    onOpenWork: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = LocalRepository.current
    val vm: CreatorViewModel = viewModel(
        factory = viewModelFactory { initializer { CreatorViewModel(repo) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val c = JmTheme.colors

    Column(modifier = modifier.fillMaxSize()) {
        GlassTopBar(
            title = state.authorName ?: "画师与作品库",
            subtitle = if (state.total > 0) "共 ${state.total} 项" else "来自作品库的画师与作品",
            navigation = {
                IconButton(
                    onClick = { if (state.authorId != null) vm.closeAuthor() else onBack() },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = c.accent,
                    )
                }
            },
        )

        if (state.authorId == null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                CreatorTab.entries.forEach { tab ->
                    FilterChip(
                        selected = state.tab == tab,
                        onClick = { vm.setTab(tab) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = { vm.setQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                placeholder = {
                    Text(
                        text = if (state.tab == CreatorTab.Authors) "搜索画师" else "搜索作品",
                        color = c.textTertiary,
                    )
                },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { vm.load() }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索", tint = c.accent)
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { vm.load() },
                ),
            )
        }

        when {
            state.loading -> LoadingBox()

            state.error != null && state.authors.isEmpty() && state.works.isEmpty() ->
                ErrorBox(message = state.error.orEmpty(), onRetry = { vm.load() })

            state.tab == CreatorTab.Authors && state.authorId == null ->
                if (state.authors.isEmpty()) {
                    MessageState(title = "没有找到画师", icon = Icons.Filled.Brush, onRetry = { vm.load() })
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 168.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        items(state.authors, key = { it.id }) { author ->
                            AuthorCard(
                                author = author,
                                avatarUrl = repo.artistIconUrl(author),
                                onClick = { vm.openAuthor(author) },
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

            else -> if (state.works.isEmpty()) {
                MessageState(title = "没有找到作品", icon = Icons.AutoMirrored.Filled.MenuBook, onRetry = { vm.load() })
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = CardSizes.grid),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(state.works, key = { it.id }) { work ->
                        WorkCard(
                            work = work,
                            coverUrl = repo.creatorWorkCoverUrl(work),
                            onClick = { onOpenWork(work.id) },
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
}

/** 画师卡片：头像 + 名字 + 最近更新。 */
@Composable
private fun AuthorCard(author: CreatorAuthor, avatarUrl: String?, onClick: () -> Unit) {
    val c = JmTheme.colors
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        level = GlassLevel.Card,
        shape = jmShape(Radius.md),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = author.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(percent = 50)),
            )
            Column(Modifier.weight(1f).padding(start = Spacing.sm)) {
                Text(
                    text = author.name.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.text,
                    maxLines = 2,
                )
                author.updateDate?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                }
            }
        }
    }
}

/** 作品卡片：封面 + 标题 + 来源平台。 */
@Composable
private fun WorkCard(work: CreatorWork, coverUrl: String?, onClick: () -> Unit) {
    val c = JmTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(jmShape(Radius.md))
                .background(c.surfaceSunken),
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = work.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(jmShape(Radius.md)),
            )
        }
        Text(
            text = work.title.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = c.text,
            maxLines = 2,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        work.platform?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 1)
        }
    }
}
