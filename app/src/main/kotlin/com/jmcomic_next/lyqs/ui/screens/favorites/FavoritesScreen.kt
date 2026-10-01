package com.jmcomic_next.lyqs.ui.screens.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.remote.dto.FavoriteFolder
import com.jmcomic_next.lyqs.data.remote.dto.ListItem
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.components.ComicRow
import com.jmcomic_next.lyqs.ui.components.ErrorBox
import com.jmcomic_next.lyqs.ui.components.GlassTopBar
import com.jmcomic_next.lyqs.ui.components.LoadingBox
import com.jmcomic_next.lyqs.ui.components.MessageState
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 账号相关列表的两种形态。两者的数据形态与交互一致，因此共用一个 ViewModel。 */
enum class AccountListKind(val title: String, val icon: ImageVector, val emptyHint: String) {
    Favorites("我的收藏", Icons.Filled.BookmarkBorder, "还没有收藏，去详情页点收藏试试"),
    History("观看历史", Icons.Filled.History, "还没有观看记录"),
}

data class AccountListUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val loggedIn: Boolean = false,
    val items: List<ListItem> = emptyList(),
    val folders: List<FavoriteFolder> = emptyList(),
    val selectedFolder: String = "",
    val total: Int = 0,
    val loadingMore: Boolean = false,
)

class AccountListViewModel(
    private val repo: JmRepository,
    private val kind: AccountListKind,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountListUiState())
    val state: StateFlow<AccountListUiState> = _state.asStateFlow()

    /** 收藏每页 20 条、历史每页 20 条，都与官方一致。 */
    private val pageSize = 20
    private var page = 1

    init {
        load()
    }

    fun load() {
        val loggedIn = repo.auth.isLoggedIn
        _state.update { it.copy(loading = true, error = null, loggedIn = loggedIn) }
        if (!loggedIn) {
            _state.update { it.copy(loading = false) }
            return
        }

        viewModelScope.launch {
            page = 1
            val result = runCatching {
                repo.bootstrap()
                when (kind) {
                    AccountListKind.Favorites -> {
                        val p = repo.favorites(page = 1, folderId = _state.value.selectedFolder)
                        Triple(p.list, p.folderList, p.totalCount)
                    }
                    AccountListKind.History -> {
                        val p = repo.history(page = 1)
                        Triple(p.list, emptyList(), p.totalCount)
                    }
                }
            }
            _state.update {
                it.copy(
                    loading = false,
                    items = result.getOrNull()?.first.orEmpty(),
                    folders = result.getOrNull()?.second.orEmpty(),
                    total = result.getOrNull()?.third ?: 0,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun selectFolder(folderId: String) {
        if (_state.value.selectedFolder == folderId) return
        // 换收藏夹时先清空列表，避免旧夹的内容停留一瞬造成误读
        _state.update { it.copy(selectedFolder = folderId, items = emptyList()) }
        load()
    }

    /**
     * 删除一条历史。
     *
     * 只有历史列表支持；收藏的移除走详情页的收藏按钮（服务端是切换式）。
     * 本地先移除再发请求：删除是明确的用户意图，失败时用错误提示告知并把列表恢复。
     */
    fun deleteHistory(comicId: String) {
        if (kind != AccountListKind.History) return
        val before = _state.value.items
        _state.update { it.copy(items = it.items.filterNot { c -> c.id == comicId }) }
        viewModelScope.launch {
            val result = runCatching { repo.deleteHistory(comicId) }
            if (result.isFailure) {
                _state.update { it.copy(items = before, error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.items.isEmpty()) return
        if (s.total > 0 && s.items.size >= s.total) return

        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val next = page + 1
            val result = runCatching {
                when (kind) {
                    AccountListKind.Favorites ->
                        repo.favorites(page = next, folderId = s.selectedFolder).list
                    AccountListKind.History -> repo.history(page = next).list
                }
            }
            _state.update { prev ->
                val more = result.getOrDefault(emptyList())
                if (result.isSuccess && more.isNotEmpty()) page = next
                prev.copy(
                    loadingMore = false,
                    items = if (result.isSuccess) prev.items + more else prev.items,
                )
            }
        }
    }
}

/**
 * 我的收藏 / 观看历史。
 *
 * 两者共用一屏：数据形态、分页方式与交互完全一致，差别只有接口和「收藏有收藏夹」这一点。
 * 未登录时不请求接口，直接给出登录入口 —— 对未登录用户发一个注定失败的请求没有意义。
 */
@Composable
fun AccountListScreen(
    kind: AccountListKind,
    onBack: () -> Unit,
    onOpenComic: (String) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = LocalRepository.current
    val vm: AccountListViewModel = viewModel(
        key = "account-list-${kind.name}",
        factory = viewModelFactory { initializer { AccountListViewModel(repo, kind) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val c = JmTheme.colors

    Column(modifier = modifier.fillMaxSize()) {
        GlassTopBar(
            title = kind.title,
            subtitle = if (state.loggedIn && state.total > 0) "共 ${state.total} 项" else null,
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

        when {
            !state.loggedIn -> MessageState(
                title = "需要登录",
                description = "${kind.title}与账号绑定，登录后在这里查看",
                icon = kind.icon,
                onRetry = onLogin,
            )

            state.loading -> LoadingBox()

            state.error != null && state.items.isEmpty() ->
                ErrorBox(message = state.error.orEmpty(), onRetry = { vm.load() })

            else -> {
                if (kind == AccountListKind.Favorites && state.folders.isNotEmpty()) {
                    FolderRow(
                        folders = state.folders,
                        selected = state.selectedFolder,
                        onSelect = { vm.selectFolder(it) },
                    )
                }

                if (state.items.isEmpty()) {
                    MessageState(title = kind.emptyHint, icon = kind.icon)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Spacing.lg,
                            end = Spacing.lg,
                            bottom = Spacing.xxl,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(state.items, key = { it.id }) { comic ->
                            Box(Modifier.fillMaxWidth()) {
                                ComicRow(
                                    item = comic,
                                    coverUrl = repo.coverUrl(comic),
                                    onClick = { onOpenComic(comic.id) },
                                    trailing = if (kind == AccountListKind.History) {
                                        {
                                            IconButton(onClick = { vm.deleteHistory(comic.id) }) {
                                                Icon(
                                                    imageVector = Icons.Filled.DeleteOutline,
                                                    contentDescription = "删除这条历史",
                                                    tint = JmTheme.colors.textTertiary,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                        item(key = "footer") {
                            LoadMoreFooter(state.loadingMore) { vm.loadMore() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(
    folders: List<FavoriteFolder>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val c = JmTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "收藏夹",
            style = MaterialTheme.typography.labelSmall,
            color = c.textTertiary,
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.sm),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            items(listOf(FavoriteFolder("", "全部")) + folders) { folder ->
                FilterChip(
                    selected = selected == folder.folderId,
                    onClick = { onSelect(folder.folderId) },
                    label = {
                        Text(
                            folder.name ?: folder.folderId,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun LoadMoreFooter(loading: Boolean, onLoadMore: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = JmTheme.colors.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
            )
        } else {
            LaunchedEffect(Unit) { onLoadMore() }
            Text(
                text = "上滑加载更多",
                style = MaterialTheme.typography.labelSmall,
                color = JmTheme.colors.textTertiary,
            )
        }
    }
}
