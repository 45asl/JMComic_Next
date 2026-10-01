package com.jmcomic_next.lyqs.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jmcomic_next.lyqs.data.JmRepository
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

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val results: List<ListItem> = emptyList(),
    /** 是否已发起过至少一次搜索 —— 用来区分「还没搜」和「搜了没结果」。 */
    val searched: Boolean = false,
)

class SearchViewModel(private val repo: JmRepository) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    fun onQueryChange(q: String) = _state.update { it.copy(query = q) }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isEmpty() || _state.value.loading) return

        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = runCatching {
                repo.bootstrap()
                repo.search(q, page = 1)
            }
            _state.update { s ->
                s.copy(
                    loading = false,
                    searched = true,
                    results = result.getOrNull()?.items.orEmpty(),
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }
}

/**
 * 搜索页。
 *
 * 目前只做关键词检索；服务端另有排序（`o`）与检索类型（`search_type`）参数，
 * 以及 `hot_tags` 热门标签 —— 留待后续做成筛选面板。
 */
@Composable
fun SearchScreen(
    onOpenComic: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
) {
    val repo = LocalRepository.current
    val vm: SearchViewModel = viewModel(
        factory = viewModelFactory { initializer { SearchViewModel(repo) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf(initialQuery) }
    val c = JmTheme.colors

    // 从分类页带着标签进来时直接开搜，省掉一次手动确认
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            vm.onQueryChange(initialQuery)
            vm.search()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        GlassTopBar(title = "搜索")

        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                vm.onQueryChange(it)
            },
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            placeholder = { Text("输入作品名或作者", color = c.textTertiary) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { vm.search() }) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索", tint = c.accent)
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search() }),
        )

        when {
            state.loading -> LoadingBox()

            state.error != null && state.results.isEmpty() ->
                ErrorBox(message = state.error.orEmpty(), onRetry = { vm.search() })

            !state.searched -> MessageState(
                title = "搜点什么",
                description = "支持按作品名、作者检索",
                icon = Icons.Filled.Search,
            )

            state.results.isEmpty() -> MessageState(
                title = "没有找到相关作品",
                description = "换个关键词试试",
                icon = Icons.Filled.Search,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    bottom = Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(state.results, key = { it.id }) { comic ->
                    Box(Modifier.fillMaxWidth()) {
                        ComicRow(
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
