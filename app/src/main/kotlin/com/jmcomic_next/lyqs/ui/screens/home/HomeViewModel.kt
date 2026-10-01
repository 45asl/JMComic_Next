package com.jmcomic_next.lyqs.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.remote.JmException
import com.jmcomic_next.lyqs.data.remote.dto.ListItem
import com.jmcomic_next.lyqs.data.remote.dto.PromoteSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 首页状态。
 *
 * 推荐区与最新列表分开记录错误：任一失败都不该让另一块空白，
 * 否则用户会以为整个应用都坏了。
 */
data class HomeUiState(
    val loading: Boolean = true,
    val sections: List<PromoteSection> = emptyList(),
    val promoteError: String? = null,
    val latest: List<ListItem> = emptyList(),
    val latestTotal: Int = 0,
    val latestError: String? = null,
    val loadingMore: Boolean = false,
)

class HomeViewModel(private val repo: JmRepository) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /**
     * 最新的页码。
     *
     * **从 0 开始** —— 服务端的 `latest` 是 0-indexed，源码 `Main.tsx` 里甚至写着
     * `// page 是 0-indexed（第一頁是 0）`，界面上显示的页码才 +1。
     * 若这里按 1 起算，首屏会直接跳过真正的第一页。
     */
    private var page = 0

    /** 每页条数。源码用 `Math.ceil(total / 30)` 算总页数。 */
    private val pageSize = 30

    init {
        refresh()
    }

    /** 首屏加载与手动刷新。 */
    fun refresh() {
        page = 0
        _state.update { it.copy(loading = true, promoteError = null, latestError = null) }

        viewModelScope.launch {
            // 引导（主机发现 + 配置）失败就不必再发业务请求了
            val bootstrapped = runCatching { repo.bootstrap() }
            if (bootstrapped.isFailure) {
                val msg = bootstrapped.exceptionOrNull().toUserMessage()
                _state.update { it.copy(loading = false, promoteError = msg, latestError = msg) }
                return@launch
            }

            val promote = runCatching { repo.promote() }
            val latest = runCatching { repo.latest(0) }

            _state.update {
                it.copy(
                    loading = false,
                    sections = promote.getOrDefault(emptyList()),
                    promoteError = promote.exceptionOrNull().toUserMessage(),
                    latest = latest.getOrNull()?.items.orEmpty(),
                    latestTotal = latest.getOrNull()?.total ?: 0,
                    latestError = latest.exceptionOrNull().toUserMessage(),
                )
            }
        }
    }

    /**
     * 加载更多。
     *
     * 服务端给 total 时按其判断是否到底；只给裸数组时无法预知终点，
     * 就以「本页返回为空」为界停止追加。
     */
    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.latestError != null || s.latest.isEmpty()) return
        // 有 total 时按总页数判断（与源码 hasNextPage = page < pageLimit - 1 一致）；
        // 服务端只回裸数组时无法预知终点，交给「本页为空」兜底
        if (s.latestTotal > 0 && page >= (s.latestTotal + pageSize - 1) / pageSize - 1) return

        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val next = page + 1
            val result = runCatching { repo.latest(next) }
            _state.update { state ->
                val more = result.getOrNull()?.items.orEmpty()
                if (result.isSuccess && more.isNotEmpty()) page = next
                state.copy(
                    loadingMore = false,
                    latest = if (result.isSuccess) state.latest + more else state.latest,
                    latestTotal = result.getOrNull()?.total ?: state.latestTotal,
                    latestError = if (result.isSuccess) null else result.exceptionOrNull().toUserMessage(),
                )
            }
        }
    }
}

/** 把异常转成能直接显示给用户的一句话，避免把类名与堆栈丢到界面上。 */
internal fun Throwable?.toUserMessage(): String? = when (this) {
    null -> null
    is JmException -> message
    else -> message ?: "未知错误"
}
