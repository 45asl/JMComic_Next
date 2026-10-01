package com.jmcomic_next.lyqs.ui.screens.comments

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.jmcomic_next.lyqs.data.remote.dto.CommentItem
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.components.CategoryChip
import com.jmcomic_next.lyqs.ui.components.ErrorBox
import com.jmcomic_next.lyqs.ui.components.GlassLevel
import com.jmcomic_next.lyqs.ui.components.GlassSurface
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

data class CommentsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val comments: List<CommentItem> = emptyList(),
    val total: Int = 0,
    val loadingMore: Boolean = false,
)

class CommentsViewModel(
    private val repo: JmRepository,
    private val comicId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(CommentsUiState())
    val state: StateFlow<CommentsUiState> = _state.asStateFlow()

    private var page = 1

    init {
        load()
    }

    fun load() {
        page = 1
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = runCatching {
                repo.bootstrap()
                repo.comments(comicId, page = 1)
            }
            _state.update {
                it.copy(
                    loading = false,
                    comments = result.getOrNull()?.list.orEmpty(),
                    total = result.getOrNull()?.totalCount ?: 0,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    /**
     * 加载更多。
     *
     * 服务端给了 total 就按它判断到底；只给列表时以「本页为空」为界。
     * 不做「评论数不足一页就停」的推断 —— 服务端可能对单页数量有别的上限。
     */
    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.comments.isEmpty()) return
        if (s.total > 0 && s.comments.size >= s.total) return

        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val next = page + 1
            val result = runCatching { repo.comments(comicId, page = next) }
            _state.update { prev ->
                val more = result.getOrNull()?.list.orEmpty()
                if (result.isSuccess && more.isNotEmpty()) page = next
                prev.copy(
                    loadingMore = false,
                    comments = if (result.isSuccess) prev.comments + more else prev.comments,
                    total = result.getOrNull()?.totalCount ?: prev.total,
                )
            }
        }
    }
}

/**
 * 评论区（只读）。
 *
 * 只展示，不提供发帖与投票：那需要一个已登录且被服务端信任的账号来发内容，
 * 而这个客户端的定位是阅读。评论区在这里的价值是「看看别人怎么说」。
 *
 * 做成独立页面而不是塞进详情页：评论是无限分页的，而详情页本身已经很长，
 * 两者拼在一起会让章节目录和评论互相把对方推到很远的地方。
 */
@Composable
fun CommentsScreen(
    comicId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = LocalRepository.current
    val vm: CommentsViewModel = viewModel(
        key = "comments-$comicId",
        factory = viewModelFactory { initializer { CommentsViewModel(repo, comicId) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val c = JmTheme.colors

    Column(modifier = modifier.fillMaxSize()) {
        GlassTopBar(
            title = "评论",
            subtitle = if (state.total > 0) "共 ${state.total} 条" else null,
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

            state.error != null && state.comments.isEmpty() ->
                ErrorBox(message = state.error.orEmpty(), onRetry = { vm.load() })

            state.comments.isEmpty() -> MessageState(
                title = "还没有评论",
                description = "这里会显示其他读者的留言",
                icon = Icons.Filled.ChatBubbleOutline,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(state.comments, key = { it.commentId }) { comment ->
                    CommentCard(comment, repo)
                }
                item(key = "footer") {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.loadingMore) {
                            CircularProgressIndicator(
                                color = c.accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp),
                            )
                        } else {
                            LaunchedEffect(state.comments.size) { vm.loadMore() }
                            Text(
                                text = if (state.total > 0 && state.comments.size >= state.total) {
                                    "已经到底了"
                                } else {
                                    "上滑加载更多"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = c.textTertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentCard(comment: CommentItem, repo: JmRepository) {
    val c = JmTheme.colors
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        level = GlassLevel.Card,
        shape = RoundedCornerShape(com.jmcomic_next.lyqs.ui.theme.Radius.md),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = repo.avatarUrl(comment.photo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(c.surfaceSunken),
                )
                Column(Modifier.weight(1f).padding(start = Spacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = comment.authorName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        comment.expinfo?.level?.takeIf { it > 0 }?.let { level ->
                            Box(Modifier.padding(start = Spacing.xs)) {
                                CategoryChip("Lv$level")
                            }
                        }
                    }
                    comment.addtime?.takeIf { it.isNotBlank() }?.let { time ->
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textTertiary,
                        )
                    }
                }
            }

            comment.content?.takeIf { it.isNotBlank() }?.let { body ->
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }

            // 楼中楼只报数量：展开一层会引入缩进层级与「查看更多回复」的分页，
            // 而官方在详情页也只做展示
            if (comment.replies.isNotEmpty()) {
                Text(
                    text = "${comment.replies.size} 条回复",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.accent,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}
