package com.jmcomic_next.lyqs.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.trackedOrFalse
import com.jmcomic_next.lyqs.data.prefs.ReadProgressStore
import com.jmcomic_next.lyqs.data.remote.dto.AlbumDetail
import com.jmcomic_next.lyqs.data.remote.dto.FavoriteFolder
import com.jmcomic_next.lyqs.data.remote.dto.SeriesItem
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.components.CategoryChip
import com.jmcomic_next.lyqs.ui.components.ComicCard
import com.jmcomic_next.lyqs.ui.components.ErrorBox
import com.jmcomic_next.lyqs.ui.components.GlassLevel
import com.jmcomic_next.lyqs.ui.components.GlassSurface
import com.jmcomic_next.lyqs.ui.components.GlassTopBar
import com.jmcomic_next.lyqs.ui.screens.favorites.FolderPickerDialog
import com.jmcomic_next.lyqs.ui.components.LoadingBox
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Radius
import com.jmcomic_next.lyqs.ui.plainText
import com.jmcomic_next.lyqs.ui.screens.tags.TagPickerDialog
import com.jmcomic_next.lyqs.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val detail: AlbumDetail? = null,
    val togglingFavorite: Boolean = false,
    /** 收藏操作的结果提示，展示一次后由界面清除。 */
    val favoriteNotice: String? = null,
    /**
     * 收藏成功后展示的收藏夹选择器。
     * 官方在这个时机弹出归类对话框（`FETCH_ADD_FAVORITE_THUNK` 返回 add/move/edit 后
     * 会把 `dialogOpen.folder` 置为 true），这里保持一致。
     */
    val folderPickerVisible: Boolean = false,
    val folders: List<FavoriteFolder> = emptyList(),
    /** 阅读入口。为空只表示详情还没加载出来。 */
    val readEntry: ReadEntry? = null,
    /**
     * 是否已追更。
     *
     * 与收藏不同，这个状态**没有随详情一起下发**，得单独问一次
     * （`album_sertracking?id=`，且要登录才有意义）。
     */
    val tracked: Boolean = false,
    /** 追更 / 下载 / 标签收藏的结果提示。 */
    val actionNotice: String? = null,
    /** 标签收藏选择器是否打开。 */
    val tagPickerVisible: Boolean = false,
    /** 点赞动作的结果提示。 */
    val likeNotice: String? = null,
)

/**
 * 详情页的阅读入口。
 *
 * 三种作品形态都要能读，这是关键：
 *  - 多章节：有本地进度就续读，否则从第一话开始
 *  - 单章节：`series` 只有一项，等价于「从头开始」
 *  - **无章节**：`series` 是空数组，此时**用作品 id 本身调 `comic_read`**
 *    （实测：`comic_read?id=<作品id>` 正常返回图片列表）
 *
 * 之前只从 `series` 里找章节、且要求本地有进度才显示按钮，
 * 结果无章节的作品**没有任何入口能开始阅读**。
 */
data class ReadEntry(
    val chapterId: String,
    /** true 表示续读（本地有进度），false 表示从头开始。 */
    val isResume: Boolean,
    /** 在目录中的序号；0 表示该作品没有目录。 */
    val index: Int,
) {
    val label: String
        get() = when {
            isResume && index > 0 -> "继续阅读 · 第 $index 话"
            index > 0 -> "开始阅读 · 第 $index 话"
            else -> "开始阅读"
        }
}

/** 紧随初始加载之后的那次 `ON_RESUME` 刷新被吞掉的窗口（毫秒）。 */
private const val REFRESH_DEBOUNCE_MS = 1_500L

class DetailViewModel(
    private val repo: JmRepository,
    private val readProgress: ReadProgressStore,
    private val comicId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() = fetch(showLoading = true)

    /**
     * 静默刷新。
     *
     * 与 [load] 的差别是**不把 loading 置为 true**：从阅读页或收藏列表返回时，
     * 页面需要的是「校正已有状态」（例如收藏标记可能已在别处被改掉），
     * 而不是重来一次。若置 loading，`state.detail` 会短暂为 null，
     * 内容区会闪成加载态，章节目录的分页位置也会随之丢失。
     *
     * 同样地，**刷新失败也不能把已有内容清掉**：那次请求只是为了核对收藏标记，
     * 网络抖一下就整页变成「加载失败」，用户连自己在看哪一话都找不回来了。
     */
    fun refresh() {
        // 刚刚才拉过就不要再拉一次。首次打开详情页时 `ON_RESUME` 会在
        // **初始请求已经返回之后**才派发（实测间隔约 0.5 秒，因为导航转场先结束），
        // 于是「打开一次 = 两次 album」，第二次纯粹是浪费；
        // 从阅读页返回这类真正的重新进入间隔远大于这个窗口，不受影响。
        val since = System.currentTimeMillis() - lastFetchAt
        if (lastFetchAt > 0 && since < REFRESH_DEBOUNCE_MS) return
        if (_state.value.detail == null) load() else fetch(showLoading = false)
    }

    /** 是否已有一次加载在飞。 */
    private var inFlight = false

    /** 上一次请求**完成**的时刻，用于吃掉紧随其后的那次 `ON_RESUME` 刷新。 */
    private var lastFetchAt = 0L

    private fun fetch(showLoading: Boolean) {
        // `LifecycleEventEffect(ON_RESUME)` 在首次组合时就会立刻派发一次事件，
        // 若那次事件正好落在请求在飞期间，这道闸门会把它挡掉
        if (inFlight) return
        inFlight = true
        if (showLoading) {
            _state.update { it.copy(loading = true, error = null) }
        }
        viewModelScope.launch {
            val result = runCatching {
                repo.bootstrap()
                repo.album(comicId)
            }
            inFlight = false
            lastFetchAt = System.currentTimeMillis()
            val fresh = result.getOrNull()

            _state.update { prev ->
                // 失败时保留上一次的内容与阅读入口，且只在「什么都没有」时报错
                val detail = fresh ?: prev.detail
                val series = fresh?.series.orEmpty()

                // 仅当记录的那一话仍存在于目录里才算「续读」：目录会随作品改版变化，
                // 指向一个不存在的章节会让用户点一下就报错
                val saved = readProgress.lastChapterId(comicId)
                    ?.takeIf { id -> series.any { it.id == id } }

                val entry = when {
                    fresh == null -> prev.readEntry
                    // 读到第一话时不叫「继续」，与从头开始没有区别
                    saved != null && saved != series.firstOrNull()?.id ->
                        ReadEntry(saved, isResume = true, index = series.indexOfFirst { it.id == saved } + 1)
                    series.isNotEmpty() ->
                        ReadEntry(series.first().id, isResume = false, index = 1)
                    else ->
                        // 无章节作品：作品 id 自身就是可读单元
                        ReadEntry(comicId, isResume = false, index = 0)
                }

                prev.copy(
                    loading = false,
                    detail = detail,
                    readEntry = entry,
                    error = if (detail == null) result.exceptionOrNull()?.message else null,
                )
            }
            // 追更状态要单独问一次（详情里没有这个字段），未登录时不必问
            if (fresh != null && repo.auth.isLoggedIn) refreshTracking()
        }
    }

    /**
     * 查询追更状态。
     *
     * 失败就保持「未追更」而不提示：这只是页面上的一个标记，
     * 为它弹错误反而会让人以为详情页出了问题。
     */
    private fun refreshTracking() {
        viewModelScope.launch {
            val tracked = runCatching { repo.isTracked(comicId) }.getOrDefault(false)
            _state.update { it.copy(tracked = tracked) }
        }
    }

    /**
     * 切换追更。
     *
     * 与收藏同一套路：**同一个 POST 既是追更也是取关**，结果以服务端返回的文案为准，
     * 界面状态跟着服务端走而不是本地取反。
     */
    fun toggleTracking(onNeedLogin: (String) -> Unit) {
        if (!repo.auth.isLoggedIn) {
            onNeedLogin("追更需要登录")
            return
        }
        viewModelScope.launch {
            val result = runCatching { repo.toggleTracking(comicId) }
            val action = result.getOrNull()
            val ok = result.isSuccess && (action == null || action.isOk)
            val message = action?.msg
            _state.update {
                it.copy(
                    // **不乐观翻转**：这个接口返回的就是一句人话（「已追踪!」/「已取消追踪!」），
                    // 拿它当权威最稳。原先按「本地取反」写，一旦本地状态与服务器不一致
                    // （比如状态查询不可靠时），界面就会显示成与实际相反的状态。
                    tracked = if (ok && message != null) message.trackedOrFalse() else it.tracked,
                    actionNotice = message ?: result.exceptionOrNull()?.message
                        ?: if (ok) "已更新追更状态" else "追更失败",
                )
            }
        }
    }

    /** 打开 / 关闭标签收藏选择器。 */
    fun setTagPicker(visible: Boolean) = _state.update { it.copy(tagPickerVisible = visible) }

    /**
     * 把选中的标签加进收藏（一次提交一串，与官方一致）。
     *
     * 未登录时先引导登录：这个接口要凭证，直接发只会拿回 401 ——
     * 与点赞/收藏/追更保持一致，不让用户白点一次。
     */
    fun favoriteTags(tags: List<String>, onNeedLogin: (String) -> Unit) {
        if (tags.isEmpty()) {
            setTagPicker(false)
            return
        }
        if (!repo.auth.isLoggedIn) {
            setTagPicker(false)
            onNeedLogin("收藏标签需要登录")
            return
        }
        viewModelScope.launch {
            val result = runCatching { repo.updateFavoriteTags("add", tags) }
            val action = result.getOrNull()
            _state.update {
                it.copy(
                    tagPickerVisible = false,
                    actionNotice = action?.msg ?: result.exceptionOrNull()?.message
                        ?: "已收藏 ${tags.size} 个标签",
                )
            }
        }
    }

    /**
     * 取整部作品的下载链接。
     *
     * 需要登录，而且**失败不是 401**：实测未登录时是 HTTP 200 + `{"status":"0","msg":"請先登入"}`，
     * 所以判断落在 `status` 上；把这种响应当成功会给出一个空链接。
     */
    fun requestDownload(onNeedLogin: (String) -> Unit, onReady: (String, String) -> Unit) {
        if (!repo.auth.isLoggedIn) {
            onNeedLogin("下载需要登录")
            return
        }
        viewModelScope.launch {
            val result = runCatching { repo.albumDownload(comicId) }
            val payload = result.getOrNull()
            when {
                result.isFailure -> _state.update {
                    it.copy(actionNotice = "获取下载地址失败：${result.exceptionOrNull()?.message}")
                }

                payload == null || !payload.isOk -> _state.update {
                    it.copy(actionNotice = payload?.msg ?: "这个作品暂时不能下载")
                }

                else -> {
                    val label = buildString {
                        append("已开始下载")
                        payload.title?.takeIf { it.isNotBlank() }?.let { append("：$it") }
                        payload.fileSize?.takeIf { it.isNotBlank() }?.let { append("（$it）") }
                    }
                    _state.update { it.copy(actionNotice = label) }
                    onReady(payload.downloadUrl.orEmpty(), payload.title.orEmpty())
                }
            }
        }
    }

    fun consumeActionNotice() = _state.update { it.copy(actionNotice = null) }

    fun consumeFavoriteNotice() = _state.update { it.copy(favoriteNotice = null) }

    fun consumeLikeNotice() = _state.update { it.copy(likeNotice = null) }

    /**
     * 点赞。
     *
     * 服务端允许匿名点赞，但官方仍要求登录后才可点（未登录时提示去登录），这里保持一致：
     * 匿名点赞无法撤回也无法与账号关联，对用户没有实际价值。
     *
     * 点赞数是服务端计数，这里只做**乐观 +1**，不做本地持久化 ——
     * 真正的口径以服务端为准，下次拉详情会被纠正。
     */
    fun like(onNeedLogin: (String) -> Unit) {
        if (!repo.auth.isLoggedIn) {
            // 直接把「为什么」交给调用方显示：只说「去登录」而不说原因，
            // 用户不知道这次点击是没生效还是等登录后会自动补上
            onNeedLogin("点赞需要登录")
            return
        }
        val detail = _state.value.detail ?: return
        if (detail.liked) {
            _state.update { it.copy(likeNotice = "已经点过赞了") }
            return
        }
        viewModelScope.launch {
            val result = runCatching { repo.like(comicId) }
            val action = result.getOrNull()
            val ok = action?.isOk == true
            _state.update { prev ->
                prev.copy(
                    likeNotice = action?.msg ?: result.exceptionOrNull()?.message,
                    detail = if (ok) {
                        prev.detail?.copy(liked = true, likes = prev.detail.likes + 1)
                    } else {
                        prev.detail
                    },
                )
            }
        }
    }

    fun dismissFolderPicker() = _state.update { it.copy(folderPickerVisible = false) }

    /** 把当前作品移入指定收藏夹。 */
    fun moveToFolder(folderId: String) {
        _state.update { it.copy(folderPickerVisible = false) }
        viewModelScope.launch {
            val result = runCatching { repo.editFavoriteFolder("move", folderId = folderId, aid = comicId) }
            _state.update {
                it.copy(favoriteNotice = result.getOrNull()?.msg ?: result.exceptionOrNull()?.message)
            }
        }
    }

    /**
     * 切换收藏。
     *
     * 未登录时不发请求，直接把用户引到登录页 —— 服务端必然拒绝，
     * 让用户看着按钮转一圈再报错是更差的体验。
     *
     * 收藏结果以服务端返回的 `type` 为准（`remove` 即已取消，`add`/`move`/`edit` 即已收藏），
     * 而不是本地取反：这样即使本地状态早已过时（例如在别处操作过），界面也会被纠正回真实状态。
     */
    fun toggleFavorite(onNeedLogin: (String) -> Unit) {
        if (_state.value.togglingFavorite) return
        if (!repo.auth.isLoggedIn) {
            onNeedLogin("收藏需要登录")
            return
        }
        _state.update { it.copy(togglingFavorite = true) }
        viewModelScope.launch {
            val result = runCatching { repo.toggleFavorite(comicId) }
            val action = result.getOrNull()
            val becameFavorite = when (action?.type) {
                "remove" -> false
                "add", "move", "edit" -> true
                else -> _state.value.detail?.isFavorite ?: false
            }
            // 只有「刚收藏/移动」才引导归类；取消收藏时弹选择器毫无意义
            val shouldOfferFolder = action != null && action.type in setOf("add", "move", "edit")
            val folders = if (shouldOfferFolder) {
                runCatching { repo.favorites(page = 1).folderList }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            _state.update { prev ->
                prev.copy(
                    togglingFavorite = false,
                    detail = prev.detail?.copy(isFavorite = becameFavorite),
                    favoriteNotice = action?.msg ?: result.exceptionOrNull()?.message,
                    // 没有收藏夹时不弹空对话框，否则用户只会看到一个「关闭」按钮
                    folderPickerVisible = shouldOfferFolder && folders.isNotEmpty(),
                    folders = folders,
                )
            }
        }
    }
}

/**
 * 漫画详情。
 *
 * 章节目录**每 10 章一页**，与源码 `Detail.tsx` 的 `chunkSize = 10` 保持一致：
 * 长篇动辄数百话，一次性铺开会难以定位。
 */
@Composable
fun DetailScreen(
    comicId: String,
    onBack: () -> Unit,
    onOpenComic: (String) -> Unit,
    onReadChapter: (String) -> Unit,
    onOpenTag: (String) -> Unit,
    onNeedLogin: (String) -> Unit,
    onOpenComments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = LocalRepository.current
    val context = androidx.compose.ui.platform.LocalContext.current

    /**
     * 拿到下载地址后交给**系统下载器**（DownloadManager）。
     *
     * 不自己写下载：系统那个天然支持后台、断点续传、通知栏进度，而且存到公共下载目录
     * 不需要任何存储权限。这里只负责把 URL 与文件名交出去。
     */
    val onDownloadReady: (String, String) -> Unit = { url, title ->
        runCatching {
            val fileName = title.ifBlank { "jmcomic-$comicId" }
                .replace(Regex("""[/\\:*?"<>|]"""), "_")
                .take(80)
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                .setTitle(fileName)
                .setNotificationVisibility(
                    android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
            val manager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE)
                as android.app.DownloadManager
            manager.enqueue(request)
        }.onFailure {
            // 系统下载器不可用（极少数定制系统）时退回浏览器，至少让用户能拿到文件
            runCatching {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(url),
                    )
                )
            }
        }
    }
    val readProgress = remember(context) { ReadProgressStore(context) }
    val vm: DetailViewModel = viewModel(
        key = "detail-$comicId",
        factory = viewModelFactory { initializer { DetailViewModel(repo, readProgress, comicId) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val c = JmTheme.colors

    // 收藏结果的提示展示一次即可，避免切走再回来还挂着
    LaunchedEffect(state.favoriteNotice) {
        if (state.favoriteNotice != null) {
            kotlinx.coroutines.delay(2500)
            vm.consumeFavoriteNotice()
        }
    }
    // 回到本页时静默校正一次：收藏状态可能在收藏列表里被改过
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    LaunchedEffect(state.likeNotice) {
        if (state.likeNotice != null) {
            kotlinx.coroutines.delay(2500)
            vm.consumeLikeNotice()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        GlassTopBar(
            title = state.detail?.name ?: "作品详情",
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
                state.detail?.let { detail ->
                    // 追更：状态要单独查（详情里没有这个字段），因此未登录时不显示，
                    // 免得给出一个必然失败、还得多跳一次登录页的按钮
                    if (repo.auth.isLoggedIn) {
                        IconButton(onClick = { vm.toggleTracking(onNeedLogin) }) {
                            Icon(
                                imageVector = if (state.tracked) {
                                    Icons.Filled.NotificationsActive
                                } else {
                                    Icons.Filled.NotificationsNone
                                },
                                contentDescription = if (state.tracked) "取消追更" else "追更",
                                tint = if (state.tracked) c.accent else c.textSecondary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    IconButton(
                        onClick = { vm.toggleFavorite(onNeedLogin) },
                        enabled = !state.togglingFavorite,
                    ) {
                        Icon(
                            imageVector = if (detail.isFavorite) {
                                Icons.Filled.Bookmark
                            } else {
                                Icons.Filled.BookmarkBorder
                            },
                            contentDescription = if (detail.isFavorite) "取消收藏" else "收藏",
                            tint = if (detail.isFavorite) c.accent else c.textSecondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            },
        )

        (state.actionNotice ?: state.favoriteNotice ?: state.likeNotice).plainText()?.let { notice ->
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                level = GlassLevel.Card,
                shape = RoundedCornerShape(0.dp),
            ) {
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.text,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
            }
        }

        when {
            state.loading && state.detail == null -> LoadingBox()

            state.error != null && state.detail == null ->
                ErrorBox(message = state.error.orEmpty(), onRetry = { vm.load() })

            state.detail != null -> DetailContent(
                detail = state.detail!!,
                repo = repo,
                readEntry = state.readEntry,
                onLike = { vm.like(onNeedLogin) },
                onOpenComic = onOpenComic,
                onReadChapter = onReadChapter,
                onOpenTag = onOpenTag,
                onOpenComments = onOpenComments,
                onDownload = { vm.requestDownload(onNeedLogin, onDownloadReady) },
                onOpenTagPicker = { vm.setTagPicker(true) },
            )

            // 兜底：加载结束却没有内容也没有错误（例如异常没有 message）时，
            // 之前这里什么都不渲染，用户面对的是一张只有顶栏的白屏，且没有重试入口
            else -> ErrorBox(message = "没能加载出这部作品", onRetry = { vm.load() })
        }
    }

    if (state.tagPickerVisible) {
        TagPickerDialog(
            tags = state.detail?.tags.orEmpty(),
            onDismiss = { vm.setTagPicker(false) },
            onConfirm = { tags -> vm.favoriteTags(tags, onNeedLogin) },
        )
    }

    if (state.folderPickerVisible) {
        FolderPickerDialog(
            title = "移入收藏夹",
            folders = state.folders,
            onDismiss = { vm.dismissFolderPicker() },
            onPick = { folder -> vm.moveToFolder(folder.folderId) },
            onSkip = { vm.dismissFolderPicker() },
            skipLabel = "仅收藏，不归类",
        )
    }
}

@Composable
private fun DetailContent(
    detail: AlbumDetail,
    repo: JmRepository,
    readEntry: ReadEntry?,
    onOpenComic: (String) -> Unit,
    onReadChapter: (String) -> Unit,
    onOpenTag: (String) -> Unit,
    onLike: () -> Unit,
    onOpenComments: () -> Unit,
    onDownload: () -> Unit,
    onOpenTagPicker: () -> Unit,
) {
    val c = JmTheme.colors
    // 默认停在第一章所在的那一页目录
    var chapterPage by rememberSaveable { mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        // 头部：封面 + 元信息
        item(key = "head") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                AsyncImage(
                    model = repo.coverUrl(detail.id, detail.addTime),
                    contentDescription = detail.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(112.dp)
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(Radius.md)),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = detail.name.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        color = c.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail.author.isNotEmpty()) {
                        Text(
                            text = "作者：" + detail.author.joinToString("、"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = buildString {
                            append("共 ${detail.totalPhotos} 页")
                            if (detail.commentTotal > 0) append(" · ${detail.commentTotal} 评论")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary,
                    )
                    // 点赞：未点过用描边心形，点过用实心强调色
                    Surface(
                        shape = RoundedCornerShape(Radius.xs),
                        color = if (detail.liked) c.accentSoft else c.surfaceSunken,
                        onClick = onLike,
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = Spacing.sm,
                                vertical = Spacing.xxs,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (detail.liked) {
                                    Icons.Filled.Favorite
                                } else {
                                    Icons.Filled.FavoriteBorder
                                },
                                contentDescription = if (detail.liked) "已点赞" else "点赞",
                                tint = if (detail.liked) c.accent else c.textTertiary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = " ${detail.likes}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (detail.liked) c.accent else c.textSecondary,
                            )
                        }
                    }
                    detail.addTime?.takeIf { it.isNotBlank() }?.let {
                        Text("更新：$it", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                    }
                }
            }
        }

        // 作者（可点，跳同作者搜索）
        if (detail.author.isNotEmpty()) {
            item(key = "authors") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(detail.author) { author ->
                        CategoryChip(author, onClick = { onOpenTag(author) })
                    }
                }
            }
        }

        // 作品 / 演员：这两个字段此前被忽略，但它们是官方详情页展示的一部分
        val metaRows = buildList {
            if (detail.works.isNotEmpty()) add("作品" to detail.works)
            if (detail.actors.isNotEmpty()) add("演员" to detail.actors)
        }
        metaRows.forEach { (label, values) ->
            item(key = "meta-$label") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textTertiary,
                        )
                    }
                    items(values) { value ->
                        CategoryChip(value, onClick = { onOpenTag(value) })
                    }
                }
            }
        }

        // 标签
        if (detail.tags.isNotEmpty()) {
            item(key = "tags") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(detail.tags) { tag ->
                        CategoryChip(tag, onClick = { onOpenTag(tag) })
                    }
                }
            }
        }

        // 简介
        detail.description?.takeIf { it.isNotBlank() }?.let { desc ->
            item(key = "desc") {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                    level = GlassLevel.Card,
                ) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.textSecondary,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            }
        }

        // 下载：官方是独立页面（`/comic/detail/download`）并配了一段下载说明与验证码，
        // 这里做成一个动作 —— 拿到服务端给的 download_url 后交给系统下载器，
        // 不在应用里另造一个下载管理器（系统那个能断点续传、能后台、能通知）。
        item(key = "download") {
            GlassSurface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                level = GlassLevel.Card,
                onClick = onDownload,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "下载整部作品",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.text,
                        modifier = Modifier.weight(1f).padding(start = Spacing.sm),
                    )
                    Text(
                        text = "交给系统下载器",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary,
                    )
                }
            }
        }

        // 标签收藏：把这部作品的标签加进「我的 → 标签收藏」
        if (detail.tags.isNotEmpty()) {
            item(key = "tag-favorite") {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                    level = GlassLevel.Card,
                    onClick = onOpenTagPicker,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BookmarkAdd,
                            contentDescription = null,
                            tint = c.accent,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "收藏这些标签",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.text,
                            modifier = Modifier.weight(1f).padding(start = Spacing.sm),
                        )
                    }
                }
            }
        }

        // 评论入口。做成入口而不是内嵌列表：评论是无限分页的，
        // 内嵌会把章节目录推到很远，而两者都是用户会主动去找的内容，不该互相挡路
        item(key = "comments") {
            GlassSurface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                level = GlassLevel.Card,
                onClick = onOpenComments,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChatBubbleOutline,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "评论",
                        style = MaterialTheme.typography.titleMedium,
                        color = c.text,
                        modifier = Modifier.weight(1f).padding(start = Spacing.sm),
                    )
                    Text(
                        text = if (detail.commentTotal > 0) "${detail.commentTotal} 条" else "暂无",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textTertiary,
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = c.textTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // 阅读入口：**任何作品形态都必须有**。放在最前，它是本页最主要的动作。
        // 无章节的作品（series 为空）同样有入口，走作品 id 自身。
        readEntry?.let { entry ->
            item(key = "read-entry") {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                    level = GlassLevel.Raised,
                    tinted = true,
                    onClick = { onReadChapter(entry.chapterId) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = c.accent,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = c.text,
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                }
            }
        }

        // 章节目录：只有一项时列表没有意义（上面已有「开始阅读」），故不展示
        if (detail.series.size > 1) {
            item(key = "chapters-head") {
                Text(
                    text = "章节目录（${detail.series.size}）",
                    style = MaterialTheme.typography.titleLarge,
                    color = c.text,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
            item(key = "chapters") {
                ChapterPager(
                    series = detail.series,
                    page = chapterPage,
                    onPageChange = { chapterPage = it },
                    onReadChapter = onReadChapter,
                )
            }
        }

        // 相关推荐
        if (detail.relatedList.isNotEmpty()) {
            item(key = "related-head") {
                Text(
                    text = "相关推荐",
                    style = MaterialTheme.typography.titleLarge,
                    color = c.text,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
            item(key = "related") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(detail.relatedList, key = { it.id }) { comic ->
                        ComicCard(
                            item = comic,
                            coverUrl = repo.coverUrl(comic),
                            onClick = { onOpenComic(comic.id) },
                        )
                    }
                }
            }
        }
    }
}

/** 章节目录分页器。每页 10 章，与源码一致。 */
@Composable
private fun ChapterPager(
    series: List<SeriesItem>,
    page: Int,
    onPageChange: (Int) -> Unit,
    onReadChapter: (String) -> Unit,
) {
    val c = JmTheme.colors
    val chunkSize = 10
    val pageCount = (series.size + chunkSize - 1) / chunkSize
    val safePage = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    val slice = series.drop(safePage * chunkSize).take(chunkSize)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        slice.forEachIndexed { index, chapter ->
            val number = safePage * chunkSize + index + 1
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                level = GlassLevel.Card,
                onClick = { onReadChapter(chapter.id) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = chapter.sort ?: number.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = c.accent,
                        modifier = Modifier.width(48.dp),
                    )
                    Text(
                        text = chapter.name?.takeIf { it.isNotBlank() } ?: "第 $number 话",
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (pageCount > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onPageChange(safePage - 1) },
                    enabled = safePage > 0,
                ) {
                    Icon(
                        Icons.Filled.ChevronLeft,
                        contentDescription = "上一页",
                        tint = if (safePage > 0) c.accent else c.textTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                TextButton(onClick = { onPageChange(0) }) {
                    Text("${safePage + 1} / $pageCount", color = c.textSecondary)
                }
                IconButton(
                    onClick = { onPageChange(safePage + 1) },
                    enabled = safePage < pageCount - 1,
                ) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "下一页",
                        tint = if (safePage < pageCount - 1) c.accent else c.textTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
