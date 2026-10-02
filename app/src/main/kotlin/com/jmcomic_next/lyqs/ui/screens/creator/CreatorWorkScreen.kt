package com.jmcomic_next.lyqs.ui.screens.creator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.remote.dto.CreatorWorkContent
import com.jmcomic_next.lyqs.data.remote.dto.CreatorWorkInfo
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.components.ComicCard
import com.jmcomic_next.lyqs.ui.components.ErrorBox
import com.jmcomic_next.lyqs.ui.components.GlassTopBar
import com.jmcomic_next.lyqs.ui.components.LoadingBox
import com.jmcomic_next.lyqs.ui.components.MessageState
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Radius
import com.jmcomic_next.lyqs.ui.theme.Spacing
import com.jmcomic_next.lyqs.ui.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreatorWorkUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val info: CreatorWorkInfo? = null,
    val content: CreatorWorkContent? = null,
)

/**
 * 作品库里的一个作品。
 *
 * 两个接口：`creator_work_info` 给作品信息与一组相关作品，`creator_work_info_detail` 给内容
 * （`images` + 正文）。**第二个不保证有东西** —— 实测有的作品回 `total_page: 0` / `images: []`，
 * 因此界面上要把它当作「这个作品没有可看的图」而不是错误。
 *
 * 注意这里的作品 id 与漫画的 album id **不是同一套编号**：作品库是画师的平台作品
 * （patreon / fanbox 等），点击不会进漫画详情页。
 */
class CreatorWorkViewModel(
    private val repo: JmRepository,
    private val workId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(CreatorWorkUiState())
    val state: StateFlow<CreatorWorkUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = runCatching {
                repo.bootstrap()
                // 内容那一半可能失败（有些作品没有），失败不该让整页报错
                val info = repo.creatorWorkInfo(workId)
                val content = runCatching { repo.creatorWorkContent(workId) }.getOrNull()
                info to content
            }
            _state.update {
                it.copy(
                    loading = false,
                    info = result.getOrNull()?.first,
                    content = result.getOrNull()?.second,
                    error = result.exceptionOrNull()?.toUserMessage(),
                )
            }
        }
    }
}

@Composable
fun CreatorWorkScreen(
    workId: String,
    onBack: () -> Unit,
    onOpenWork: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = LocalRepository.current
    val vm: CreatorWorkViewModel = viewModel(
        key = "creator-work-$workId",
        factory = viewModelFactory { initializer { CreatorWorkViewModel(repo, workId) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val c = JmTheme.colors
    // 取成局部变量：`state` 是委托属性，智能转换不会跨进 LazyColumn 的 lambda
    val info = state.info

    Column(modifier = modifier.fillMaxSize()) {
        GlassTopBar(
            title = info?.title?.takeIf { it.isNotBlank() } ?: "作品信息",
            subtitle = info?.authorName?.takeIf { it.isNotBlank() }?.let { "作者：$it" },
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

            info == null -> MessageState(
                title = "没有这个作品的信息",
                icon = Icons.Filled.MenuBook,
                onRetry = { vm.load() },
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item(key = "meta") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
                        info.date?.takeIf { it.isNotBlank() }?.let {
                            Text("日期：$it", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                        }
                        info.authorName?.takeIf { it.isNotBlank() }?.let {
                            Text("作者：$it", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                        }
                    }
                }

                val images = state.content?.images.orEmpty()
                if (images.isNotEmpty()) {
                    items(images.size, key = { "img-$it" }) { index ->
                        AsyncImage(
                            model = repo.creatorContentUrl(images[index].image),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.lg)
                                .clip(RoundedCornerShape(Radius.sm)),
                        )
                    }
                }

                state.content?.content?.takeIf { it.isNotBlank() }?.let { text ->
                    item(key = "text") {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.text,
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        )
                    }
                }

                if (images.isEmpty() && state.content?.content.isNullOrBlank()) {
                    item(key = "empty") {
                        MessageState(
                            title = "这个作品没有可看的内容",
                            description = "作品库里有些条目只有信息，没有图片",
                            icon = Icons.Filled.MenuBook,
                        )
                    }
                }

                if (info.relatedWorks.isNotEmpty()) {
                    item(key = "related-head") {
                        Text(
                            text = "相关作品",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.text,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        )
                    }
                    item(key = "related-row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Spacing.lg),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            items(info.relatedWorks, key = { it.id }) { work ->
                                ComicCard(
                                    item = com.jmcomic_next.lyqs.data.remote.dto.ListItem(
                                        id = work.id,
                                        name = work.title,
                                    ),
                                    coverUrl = repo.creatorWorkCoverUrl(work).orEmpty(),
                                    onClick = { onOpenWork(work.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
