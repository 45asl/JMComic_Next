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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/** 评论长度上限。服务端也有上限，本地先拦住，省一次必然失败的往返。 */
private const val MAX_COMMENT_LENGTH = 500

data class CommentsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val comments: List<CommentItem> = emptyList(),
    val total: Int = 0,
    val loadingMore: Boolean = false,
    /** 续加失败的原因。不能与 [error] 混用：页脚会在失败后重新进入组合并再次自动触发。 */
    val loadMoreError: String? = null,
    /** 已经到底。 */
    val exhausted: Boolean = false,
    /** 发表评论的输入内容。 */
    val draft: String = "",
    val sending: Boolean = false,
    /** 发表 / 删除的结果提示，展示一次后清除。 */
    val notice: String? = null,
    /** 当前登录用户的 uid，用来判断哪些评论是自己发的（只有自己能删）。 */
    val selfUid: String? = null,
)

class CommentsViewModel(
    private val repo: JmRepository,
    private val comicId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(
        CommentsUiState(selfUid = repo.auth.member?.uid),
    )
    val state: StateFlow<CommentsUiState> = _state.asStateFlow()

    /** 是否已登录 —— 发表与删除都要求登录。 */
    val loggedIn: Boolean get() = repo.auth.isLoggedIn

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
        if (s.exhausted || s.loadMoreError != null) return
        if (s.total > 0 && s.comments.size >= s.total) {
            _state.update { it.copy(exhausted = true) }
            return
        }

        _state.update { it.copy(loadingMore = true, loadMoreError = null) }
        viewModelScope.launch {
            val next = page + 1
            val result = runCatching { repo.comments(comicId, page = next) }
            _state.update { prev ->
                val more = result.getOrNull()?.list.orEmpty()
                if (result.isSuccess && more.isNotEmpty()) page = next
                prev.copy(
                    loadingMore = false,
                    comments = if (result.isSuccess) prev.comments + more else prev.comments,
                    total = result.getOrNull()?.totalCount?.takeIf { it > 0 } ?: prev.total,
                    loadMoreError = if (result.isSuccess) null else result.exceptionOrNull()?.message,
                    // 成功但本页为空 = 到底了（服务端不给 total 时的唯一终点信号）
                    exhausted = result.isSuccess && more.isEmpty(),
                )
            }
        }
    }

    fun onDraftChange(text: String) = _state.update { it.copy(draft = text.take(MAX_COMMENT_LENGTH)) }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    /**
     * 发表评论。
     *
     * 两点边界写在代码里而不是文档里：
     *  1. **未登录不发**（服务端会回 401 + `{"type":"auth_fail"}`，白跑一次）
     *  2. 内容长度在本地截断（服务端也有上限，但让它先失败不如本地就拦住）
     *
     * 界面只负责把内容提交给服务端，**是否发送由用户自己按下按钮决定** —— 这是真实内容，
     * 不该由应用替他发。
     */
    fun send(onNeedLogin: () -> Unit) {
        val text = _state.value.draft.trim()
        if (text.isEmpty() || _state.value.sending) return
        if (!loggedIn) {
            onNeedLogin()
            return
        }
        _state.update { it.copy(sending = true, notice = null) }
        viewModelScope.launch {
            val result = runCatching { repo.sendComment(comicId, text) }
            val action = result.getOrNull()
            val ok = result.isSuccess && (action == null || action.isOk)
            _state.update {
                it.copy(
                    sending = false,
                    draft = if (ok) "" else it.draft,
                    notice = when {
                        ok -> action?.msg ?: "已发表"
                        else -> "发表失败：${action?.msg ?: result.exceptionOrNull()?.message ?: "未知错误"}"
                    },
                )
            }
            // 发表成功后重新拉第一页：服务端的排序由它决定，本地插入会与真实顺序不一致
            if (ok) load()
        }
    }

    /** 删除自己发的评论。只有 uid 与当前账号一致时才允许。 */
    fun delete(comment: CommentItem) {
        val self = _state.value.selfUid
        if (self == null || comment.uid != self) return
        val before = _state.value.comments
        _state.update { it.copy(comments = it.comments.filterNot { c -> c.commentId == comment.commentId }) }
        viewModelScope.launch {
            val result = runCatching { repo.deleteComment(comment.commentId, comicId) }
            val action = result.getOrNull()
            val ok = result.isSuccess && (action == null || action.isOk)
            _state.update {
                it.copy(
                    comments = if (ok) it.comments else before,
                    notice = if (ok) {
                        "已删除"
                    } else {
                        "删除失败：${action?.msg ?: result.exceptionOrNull()?.message ?: "未知错误"}"
                    },
                )
            }
        }
    }

    /** 续加失败后的重试：先清错误，否则 [loadMore] 会立刻早退。 */
    fun retryLoadMore() {
        _state.update { it.copy(loadMoreError = null) }
        loadMore()
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
    onNeedLogin: () -> Unit,
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

        // 发表评论的输入行。
        // 说明写在占位符里而不是弹窗：这是**会发到站点上的真实内容**，
        // 用户按下发送前就应该知道这一点。
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.draft,
                onValueChange = { vm.onDraftChange(it) },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = if (state.selfUid != null) "说点什么（会公开发布）" else "登录后可发表评论",
                        color = c.textTertiary,
                    )
                },
                maxLines = 3,
                enabled = !state.sending,
            )
            TextButton(
                onClick = { vm.send(onNeedLogin) },
                enabled = !state.sending && state.draft.isNotBlank(),
            ) {
                Text(if (state.sending) "发送中…" else "发送", color = c.accent)
            }
        }

        state.notice?.let { notice ->
            Text(
                text = notice,
                style = MaterialTheme.typography.labelSmall,
                color = c.accent,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )
        }

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
                    CommentCard(
                        comment = comment,
                        repo = repo,
                        // 只有自己发的才有删除入口：服务端也只会接受本人的删除请求，
                        // 给别人的评论配一个必然失败的按钮只会让人误以为能删
                        onDelete = if (state.selfUid != null && comment.uid == state.selfUid) {
                            { vm.delete(comment) }
                        } else {
                            null
                        },
                    )
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

@Composable
private fun CommentCard(
    comment: CommentItem,
    repo: JmRepository,
    onDelete: (() -> Unit)? = null,
) {
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
                    onDelete?.let { delete ->
                        IconButton(
                            onClick = delete,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "删除这条评论",
                                tint = c.textTertiary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
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
