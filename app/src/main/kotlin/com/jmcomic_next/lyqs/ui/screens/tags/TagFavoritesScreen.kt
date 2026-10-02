package com.jmcomic_next.lyqs.ui.screens.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.remote.dto.TagItem
import com.jmcomic_next.lyqs.ui.LocalRepository
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

data class TagFavoritesUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val loggedIn: Boolean = false,
    val tags: List<TagItem> = emptyList(),
    val notice: String? = null,
)

/**
 * 收藏的标签。
 *
 * 服务端上限 50 个（官方界面写的就是「共 N／50」），因此界面把计数显示出来 ——
 * 到顶之后再收藏会失败，用户需要先知道这件事。
 *
 * 字段名是 `tag`（不是 `name`/`id`）：选中、删除、跳搜索全用它。
 */
class TagFavoritesViewModel(private val repo: JmRepository) : ViewModel() {

    private val _state = MutableStateFlow(TagFavoritesUiState(loggedIn = repo.auth.isLoggedIn))
    val state: StateFlow<TagFavoritesUiState> = _state.asStateFlow()

    fun load() {
        val loggedIn = repo.auth.isLoggedIn
        _state.update { it.copy(loading = true, error = null, loggedIn = loggedIn) }
        if (!loggedIn) {
            _state.update { it.copy(loading = false, tags = emptyList()) }
            return
        }
        viewModelScope.launch {
            val result = runCatching {
                repo.bootstrap()
                repo.favoriteTags()
            }
            _state.update {
                it.copy(
                    loading = false,
                    tags = result.getOrDefault(emptyList()),
                    error = result.exceptionOrNull().toUserMessage(),
                )
            }
        }
    }

    /** 删除一个收藏标签。成功后本地移除，不再整屏重拉。 */
    fun remove(tag: String) {
        val before = _state.value.tags
        _state.update { it.copy(tags = it.tags.filterNot { t -> t.tag == tag }) }
        viewModelScope.launch {
            val result = runCatching { repo.updateFavoriteTags("remove", listOf(tag)) }
            val action = result.getOrNull()
            if (result.isFailure || (action != null && !action.isOk)) {
                val message = action?.msg ?: result.exceptionOrNull()?.message ?: "未知错误"
                _state.update { it.copy(tags = before, notice = "删除失败：$message") }
            }
        }
    }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    companion object {
        /** 服务端上限，与官方界面一致。 */
        const val LIMIT = 50
    }
}

/** 我的 → 标签收藏。 */
@Composable
fun TagFavoritesScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onOpenTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = LocalRepository.current
    val vm: TagFavoritesViewModel = viewModel(
        factory = viewModelFactory { initializer { TagFavoritesViewModel(repo) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val c = JmTheme.colors

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.load() }

    Column(modifier = modifier.fillMaxSize()) {
        GlassTopBar(
            title = "标签收藏",
            subtitle = if (state.loggedIn) "${state.tags.size} / ${TagFavoritesViewModel.LIMIT}" else null,
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

        state.notice?.let { notice ->
            Text(
                text = notice,
                style = MaterialTheme.typography.labelSmall,
                color = c.accent,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
        }

        when {
            !state.loggedIn -> MessageState(
                title = "需要登录",
                description = "标签收藏与账号绑定，登录后在这里查看",
                icon = Icons.Filled.BookmarkAdd,
                onRetry = onLogin,
            )

            state.loading -> LoadingBox()

            state.error != null && state.tags.isEmpty() ->
                ErrorBox(message = state.error.orEmpty(), onRetry = { vm.load() })

            state.tags.isEmpty() -> MessageState(
                title = "还没有收藏标签",
                description = "在作品详情页的标签上可以收藏",
                icon = Icons.Filled.BookmarkAdd,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(state.tags, key = { it.tag }) { item ->
                    Surface(
                        shape = RoundedCornerShape(Radius.xs),
                        color = c.surface1,
                        onClick = { onOpenTag(item.tag) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "#${item.tag}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.text,
                                modifier = Modifier.weight(1f).padding(vertical = Spacing.md),
                            )
                            IconButton(onClick = { vm.remove(item.tag) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "取消收藏这个标签",
                                    tint = c.textTertiary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 「收藏标签」选择对话框：把某个作品上的标签加进收藏。
 *
 * 与官方一致的地方是**多选后一次提交**（`tags_favorite_update` 收的是逗号分隔的一串），
 * 而不是每个标签各发一次请求。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPickerDialog(
    tags: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val c = JmTheme.colors
    val selected = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateListOf<String>()
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("收藏标签") },
        text = {
            Column {
                Text(
                    text = "选中的标签会加入「我的 → 标签收藏」（上限 50）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    tags.forEach { tag ->
                        val isSelected = selected.contains(tag)
                        Surface(
                            shape = RoundedCornerShape(Radius.xs),
                            color = if (isSelected) c.accentSoft else c.surface1,
                            onClick = {
                                if (isSelected) selected.remove(tag) else selected.add(tag)
                            },
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) c.accent else c.text,
                                modifier = Modifier.padding(
                                    horizontal = Spacing.sm,
                                    vertical = Spacing.xs,
                                ),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(selected.toList()) },
                enabled = selected.isNotEmpty(),
            ) {
                Text("收藏 ${selected.size} 个", color = c.accent)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("取消", color = c.textSecondary)
            }
        },
    )
}
