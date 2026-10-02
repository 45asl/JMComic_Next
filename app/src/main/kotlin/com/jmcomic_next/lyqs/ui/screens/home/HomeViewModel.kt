package com.jmcomic_next.lyqs.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.remote.dto.ListItem
import com.jmcomic_next.lyqs.data.remote.dto.PromoteSection
import com.jmcomic_next.lyqs.ui.toUserMessage
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
    /**
     * 「续加」这一路自己的错误。
     *
     * 与 [latestError] 分开是必须的：first-page 的错误表示「首页什么都没有」，
     * 而续加失败只表示「下一页没拿到」。若共用同一个字段，一次网络抖动就会
     * 让守卫条件永久成立，之后的「加载更多」全部静默早退 —— 页脚一直写着
     * 「上滑加载更多」，却再也加载不出来。
     */
    val loadMoreError: String? = null,
    /** 已经翻到底（服务端给出 total 时按总页数判断，否则以「本页为空」为准）。 */
    val latestExhausted: Boolean = false,
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
        _state.update {
            it.copy(
                loading = true,
                promoteError = null,
                latestError = null,
                loadMoreError = null,
                latestExhausted = false,
            )
        }

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
        if (s.loading || s.loadingMore || s.latest.isEmpty()) return
        if (s.latestExhausted || s.loadMoreError != null) return
        // 有 total 时按总页数判断（与源码 hasNextPage = page < pageLimit - 1 一致）；
        // 服务端只回裸数组时无法预知终点，交给「本页为空」兜底
        if (s.latestTotal > 0 && page >= (s.latestTotal + pageSize - 1) / pageSize - 1) {
            // 已知这是最后一页：直接记到底，不必再发一个注定为空的请求
            _state.update { it.copy(latestExhausted = true) }
            return
        }

        _state.update { it.copy(loadingMore = true, loadMoreError = null) }
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
                    loadMoreError = if (result.isSuccess) {
                        null
                    } else {
                        result.exceptionOrNull().toUserMessage()
                    },
                    // 请求成功但这一页是空的 —— 服务端只回裸数组时，这就是终点信号
                    latestExhausted = result.isSuccess && more.isEmpty(),
                )
            }
        }
    }

    /** 续加失败后的重试：先清掉错误，否则 [loadMore] 会立刻早退。 */
    fun retryLoadMore() {
        _state.update { it.copy(loadMoreError = null) }
        loadMore()
    }
}
