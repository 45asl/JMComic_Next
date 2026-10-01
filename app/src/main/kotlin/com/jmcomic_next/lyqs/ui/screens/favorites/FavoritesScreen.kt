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
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/**
 * 账号相关列表的两种形态。
 *
 * 数据形态与分页方式一致（都交给同一个 `ComicList` 渲染、每页 20 条），
 * 因此共用一个 ViewModel；差异只有接口与「收藏支持收藏夹」这一点。
 */
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
    /** 收藏夹操作的结果提示，展示一次后清除。 */
    val notice: String? = null,
)

class AccountListViewModel(
    private val repo: JmRepository,
    private val kind: AccountListKind,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountListUiState())
    val state: StateFlow<AccountListUiState> = _state.asStateFlow()

    /** 收藏与历史都是每页 20 条，与官方一致。 */
    private val pageSize = 20
    private var page = 1

    init {
        load()
    }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

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
        // 换夹时先清空列表，避免旧夹内容停留一瞬造成误读
        _state.update { it.copy(selectedFolder = folderId, items = emptyList()) }
        load()
    }

    // ------------------------------------------------------------------
    // 收藏夹编辑
    // ------------------------------------------------------------------

    fun createFolder(name: String) = editFolder(type = FOLDER_TYPE_ADD, folderName = name)

    fun renameFolder(folderId: String, name: String) =
        editFolder(type = FOLDER_TYPE_EDIT, folderId = folderId, folderName = name)

    fun deleteFolder(folderId: String) = editFolder(type = FOLDER_TYPE_DEL, folderId = folderId)

    fun moveToFolder(aid: String, folderId: String) =
        editFolder(type = FOLDER_TYPE_MOVE, folderId = folderId, aid = aid)

    /**
     * 收藏夹编辑的统一出口。
     *
     * 四个动作（新建/改名/删除/归类）走的是同一个接口，只是 `type` 不同，
     * 因此这里收敛成一处 —— 也保证「操作完必须刷新」这件事不会被漏掉某一个动作。
     */
    private fun editFolder(
        type: String,
        folderId: String? = null,
        folderName: String? = null,
        aid: String? = null,
    ) {
        viewModelScope.launch {
            val result = runCatching {
                repo.editFavoriteFolder(type, folderId, folderName, aid)
            }
            val message = result.getOrNull()?.msg ?: result.exceptionOrNull()?.message
            _state.update { it.copy(notice = message) }
            // 无论是新建、改名还是归类，收藏夹与列表都可能变化，统一重拉
            load()
        }
    }

    /** 删除一条历史。收藏的移除走详情页的收藏按钮（服务端是切换式）。 */
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

    companion object {
        // `favorite_folder` 接口的 type 取值，官方 FolderModal 用到 add/edit/move/del
        const val FOLDER_TYPE_ADD = "add"
        const val FOLDER_TYPE_EDIT = "edit"
        const val FOLDER_TYPE_DEL = "del"
        const val FOLDER_TYPE_MOVE = "move"
    }
}

/**
 * 我的收藏 / 观看历史。
 *
 * 未登录时不请求接口，直接给出登录入口 —— 对未登录用户发一个注定 401 的请求没有意义。
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

    var dialog by remember { mutableStateOf<FolderDialog>(FolderDialog.None) }
    val isFavorites = kind == AccountListKind.Favorites

    // 操作结果只提示一次
    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            kotlinx.coroutines.delay(2500)
            vm.consumeNotice()
        }
    }

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
            actions = {
                if (isFavorites && state.loggedIn) {
                    IconButton(onClick = { dialog = FolderDialog.Manage }) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = "管理收藏夹",
                            tint = c.accent,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            },
        )

        state.notice?.let { notice ->
            Text(
                text = notice,
                style = MaterialTheme.typography.bodyMedium,
                color = c.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
        }

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
                if (isFavorites && state.folders.isNotEmpty()) {
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
                                    trailing = if (isFavorites) {
                                        {
                                            IconButton(
                                                onClick = { dialog = FolderDialog.Move(comic.id) },
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.DriveFileMove,
                                                    contentDescription = "移入收藏夹",
                                                    tint = c.textTertiary,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                    } else {
                                        {
                                            IconButton(onClick = { vm.deleteHistory(comic.id) }) {
                                                Icon(
                                                    imageVector = Icons.Filled.DeleteOutline,
                                                    contentDescription = "删除这条历史",
                                                    tint = c.textTertiary,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
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

    // ---- 收藏夹相关对话框 ----
    when (val current = dialog) {
        FolderDialog.None -> Unit

        FolderDialog.Manage -> ManageFoldersDialog(
            folders = state.folders,
            onDismiss = { dialog = FolderDialog.None },
            onCreate = { dialog = FolderDialog.Create },
            onRename = { dialog = FolderDialog.Rename(it) },
            onDelete = { dialog = FolderDialog.Delete(it) },
        )

        FolderDialog.Create -> FolderNameDialog(
            title = "新建收藏夹",
            initialName = "",
            onDismiss = { dialog = FolderDialog.None },
            onConfirm = { name ->
                vm.createFolder(name)
                dialog = FolderDialog.None
            },
        )

        is FolderDialog.Rename -> FolderNameDialog(
            title = "重命名收藏夹",
            initialName = current.folder.name.orEmpty(),
            onDismiss = { dialog = FolderDialog.None },
            onConfirm = { name ->
                vm.renameFolder(current.folder.folderId, name)
                dialog = FolderDialog.None
            },
        )

        is FolderDialog.Delete -> FolderDeleteDialog(
            folder = current.folder,
            onDismiss = { dialog = FolderDialog.None },
            onConfirm = {
                vm.deleteFolder(current.folder.folderId)
                dialog = FolderDialog.None
            },
        )

        is FolderDialog.Move -> FolderPickerDialog(
            title = "移入收藏夹",
            folders = state.folders,
            onDismiss = { dialog = FolderDialog.None },
            onPick = { folder ->
                vm.moveToFolder(current.comicId, folder.folderId)
                dialog = FolderDialog.None
            },
        )
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
