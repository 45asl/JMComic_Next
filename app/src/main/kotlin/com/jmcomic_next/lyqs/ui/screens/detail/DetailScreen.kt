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
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    /** 上次读到的那一话（本地记录）。为空表示没读过或读的就是第一话。 */
    val lastChapterId: String? = null,
)

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

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = runCatching {
                repo.bootstrap()
                repo.album(comicId)
            }
            val detail = result.getOrNull()
            // 只在记录的那一话确实还在目录里时才提供「继续阅读」——
            // 目录可能因作品改版而变化，指向一个不存在的章节会直接报错
            val last = readProgress.lastChapterId(comicId)
                ?.takeIf { id -> detail?.series?.any { it.id == id } == true }
                // 读到第一话时没有「继续」的意义，与从头开始没区别
                ?.takeIf { it != detail?.series?.firstOrNull()?.id }

            _state.update {
                it.copy(
                    loading = false,
                    detail = detail,
                    lastChapterId = last,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun consumeFavoriteNotice() = _state.update { it.copy(favoriteNotice = null) }

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
    fun toggleFavorite(onNeedLogin: () -> Unit) {
        if (_state.value.togglingFavorite) return
        if (!repo.auth.isLoggedIn) {
            onNeedLogin()
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
    onNeedLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = LocalRepository.current
    val context = androidx.compose.ui.platform.LocalContext.current
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

        state.favoriteNotice?.let { notice ->
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
            state.loading -> LoadingBox()

            state.error != null -> ErrorBox(message = state.error.orEmpty(), onRetry = { vm.load() })

            state.detail != null -> DetailContent(
                detail = state.detail!!,
                repo = repo,
                lastChapterId = state.lastChapterId,
                onOpenComic = onOpenComic,
                onReadChapter = onReadChapter,
                onOpenTag = onOpenTag,
            )
        }
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
    lastChapterId: String?,
    onOpenComic: (String) -> Unit,
    onReadChapter: (String) -> Unit,
    onOpenTag: (String) -> Unit,
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
                            if (detail.likes > 0) append(" · ${detail.likes} 赞")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary,
                    )
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

        // 继续阅读：放在目录之前，因为它是「回到我上次的位置」这一最常用动作的入口
        lastChapterId?.let { chapterId ->
            val index = detail.series.indexOfFirst { it.id == chapterId } + 1
            item(key = "continue") {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                    level = GlassLevel.Raised,
                    tinted = true,
                    onClick = { onReadChapter(chapterId) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = c.accent,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = "继续阅读 · 第 $index 话",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.text,
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                }
            }
        }

        // 章节目录
        if (detail.series.isNotEmpty()) {
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
