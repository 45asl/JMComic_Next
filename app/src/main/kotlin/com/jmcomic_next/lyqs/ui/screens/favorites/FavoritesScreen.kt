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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.jmcomic_next.lyqs.ui.components.LoadMoreFooter
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
    /** 续加失败的原因。与 [error] 分开：见 [AccountListViewModel.loadMore]。 */
    val loadMoreError: String? = null,
    /** 已经到底（服务端给出 total 时按总数判断，否则以「本页为空」为准）。 */
    val exhausted: Boolean = false,
    /** 收藏夹操作的结果提示，展示一次后清除。 */
    val notice: String? = null,
)

class AccountListViewModel(
    private val repo: JmRepository,
    private val kind: AccountListKind,
) : ViewModel() {

    // 登录态用当前真实值做初值：默认值 false 会让已登录用户在首帧看到「需要登录」
    private val _state = MutableStateFlow(AccountListUiState(loggedIn = repo.auth.isLoggedIn))
    val state: StateFlow<AccountListUiState> = _state.asStateFlow()

    /** 收藏与历史都是每页 20 条，与官方一致。 */
    private val pageSize = 20
    private var page = 1

    // 不再在 init 里加载：改由界面的 ON_RESUME 触发，这样「从详情页取消收藏后返回」
    // 也会重新拉取，而不是显示进入本页那一刻的旧快照。
    fun consumeNotice() = _state.update { it.copy(notice = null) }

    /**
     * 拉取第一页。
     *
     * @param silent 后台刷新（页面回到前台、收藏夹编辑完）用 true：**不清空当前内容、不显示整屏转圈**。
     *   之前每次回到本页都会把 `loading` 置真，于是列表被一个居中的转圈替换掉，
     *   `LazyColumn` 离开组合、内部滚动状态随之注销 —— 深翻到第 60 条、进详情看一眼再返回，
     *   就会被打回第一条。刷新是为了让数据变新，不该顺手把用户的位置也重置掉。
     */
    fun load(silent: Boolean = false) {
        val loggedIn = repo.auth.isLoggedIn
        val keepContent = silent && _state.value.items.isNotEmpty()
        _state.update {
            it.copy(
                loading = !keepContent,
                error = null,
                loadMoreError = null,
                loggedIn = loggedIn,
            )
        }
        if (!loggedIn) {
            _state.update { it.copy(loading = false, items = emptyList(), total = 0) }
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
                // 静默刷新失败时保留原有内容，只把错误附在状态里；
                // 若把 items 一起清空，用户看到的就是「刷新一下什么都没了」
                val failed = result.isFailure && it.items.isNotEmpty()
                it.copy(
                    loading = false,
                    items = if (failed) it.items else result.getOrNull()?.first.orEmpty(),
                    folders = if (failed) it.folders else result.getOrNull()?.second.orEmpty(),
                    total = if (failed) it.total else result.getOrNull()?.third ?: 0,
                    exhausted = false,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun selectFolder(folderId: String) {
        if (_state.value.selectedFolder == folderId) return
        // 换夹时先清空列表，避免旧夹内容停留一瞬造成误读
        _state.update { it.copy(selectedFolder = folderId, items = emptyList(), exhausted = false) }
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
            // 无论是新建、改名还是归类，收藏夹与列表都可能变化，统一重拉（静默，别让列表跳回顶部）
            load(silent = true)
        }
    }

    /** 删除一条历史。收藏的移除走详情页的收藏按钮（服务端是切换式）。 */
    fun deleteHistory(comicId: String) {
        if (kind != AccountListKind.History) return
        val before = _state.value.items
        _state.update { it.copy(items = it.items.filterNot { c -> c.id == comicId }) }
        viewModelScope.launch {
            val result = runCatching { repo.deleteHistory(comicId) }
            if (result.isSuccess) {
                // 顶栏的「共 N 项」要跟着减：否则删掉一条后数目还挂着旧值，
                // 用户会怀疑是不是没删掉
                _state.update { it.copy(total = (it.total - 1).coerceAtLeast(0)) }
            } else {
                // 失败必须说出来。之前只写进 error，而 error 只在列表为空时才渲染 ——
                // 一条删除失败的表现就只是「那一行又回来了」，没有任何解释
                _state.update {
                    it.copy(
                        items = before,
                        loadMoreError = null,
                        notice = "删除失败：${result.exceptionOrNull()?.message ?: "未知错误"}",
                    )
                }
            }
        }
    }

    /**
     * 加载更多。
     *
     * 失败必须记进 [AccountListUiState.loadMoreError] 而不是 [AccountListUiState.error]：
     * 页脚的自动触发写在「非 loading」分支里，失败时它重新进入组合就会再请求一次 ——
     * 断网时表现为**无休止的重试循环**，界面上一句话都不说。有错误标记后页脚改为可点重试。
     */
    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.items.isEmpty()) return
        if (s.exhausted || s.loadMoreError != null) return
        if (s.total > 0 && s.items.size >= s.total) {
            _state.update { it.copy(exhausted = true) }
            return
        }

        _state.update { it.copy(loadingMore = true, loadMoreError = null) }
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
                    loadMoreError = if (result.isSuccess) null else result.exceptionOrNull()?.message,
                    // 成功但本页为空 = 到底了（服务端不给 total 时的唯一终点信号）
                    exhausted = result.isSuccess && more.isEmpty(),
                )
            }
        }
    }

    /** 续加失败后的重试。 */
    fun retryLoadMore() {
        _state.update { it.copy(loadMoreError = null) }
        loadMore()
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

    // 进入与返回本页时都重新拉取（含从详情页取消收藏后返回的情形）。
    // 静默刷新：保留列表与滚动位置，只是把数据换新。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.load(silent = true) }

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

            // 有内容就不整屏转圈：让列表留在组合里，滚动位置才不会被注销
            state.loading && state.items.isEmpty() -> LoadingBox()

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
