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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.jmcomic_next.lyqs.data.remote.dto.AlbumDetail
import com.jmcomic_next.lyqs.data.remote.dto.SeriesItem
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.components.CategoryChip
import com.jmcomic_next.lyqs.ui.components.ComicCard
import com.jmcomic_next.lyqs.ui.components.ErrorBox
import com.jmcomic_next.lyqs.ui.components.GlassLevel
import com.jmcomic_next.lyqs.ui.components.GlassSurface
import com.jmcomic_next.lyqs.ui.components.GlassTopBar
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
)

class DetailViewModel(
    private val repo: JmRepository,
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
            _state.update {
                it.copy(
                    loading = false,
                    detail = result.getOrNull(),
                    error = result.exceptionOrNull()?.message,
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
    modifier: Modifier = Modifier,
) {
    val repo = LocalRepository.current
    val vm: DetailViewModel = viewModel(
        key = "detail-$comicId",
        factory = viewModelFactory { initializer { DetailViewModel(repo, comicId) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val c = JmTheme.colors

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
        )

        when {
            state.loading -> LoadingBox()

            state.error != null -> ErrorBox(message = state.error.orEmpty(), onRetry = { vm.load() })

            state.detail != null -> DetailContent(
                detail = state.detail!!,
                repo = repo,
                onOpenComic = onOpenComic,
                onReadChapter = onReadChapter,
            )
        }
    }
}

@Composable
private fun DetailContent(
    detail: AlbumDetail,
    repo: JmRepository,
    onOpenComic: (String) -> Unit,
    onReadChapter: (String) -> Unit,
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
                    detail.author?.takeIf { it.isNotBlank() }?.let {
                        Text("作者：$it", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
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

        // 标签
        if (detail.tags.isNotEmpty()) {
            item(key = "tags") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(detail.tags) { tag -> CategoryChip(tag) }
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
