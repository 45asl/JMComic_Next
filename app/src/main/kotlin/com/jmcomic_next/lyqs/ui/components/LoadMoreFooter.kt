package com.jmcomic_next.lyqs.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jmcomic_next.lyqs.ui.theme.JmTheme

/**
 * 列表末尾的「续加」触发器。
 *
 * 不用滚动位置判断，而是把触发器本身渲染成列表项、在它进入组合时请求下一页 ——
 * LazyColumn / LazyVerticalGrid 只组合可见项，因此「页脚可见」等价于「已滑到底」，
 * 不需要额外的滚动监听。
 *
 * 三个状态必须分开，否则会退化成「静默发请求」：
 *  - [loading] 请求中，显示转圈
 *  - [error] 上一次续加失败：**不能**继续自动触发，否则页脚每次重新进入组合都会再发一次，
 *    在没有网络时表现为无限重试（每个往返一次请求，界面上什么都不说）。这里换成可点的重试。
 *  - [exhausted] 已经到底：仍然留在列表里但不发请求 —— 空着会让用户以为还有内容没加载出来。
 *
 * 这个组件被首页、搜索、评论、收藏/历史、分类、分区更多共用，
 * 就是为了让上面这三条在每一处都一致，而不是各页各写一遍 `LaunchedEffect(Unit)`。
 */
@Composable
fun LoadMoreFooter(
    loading: Boolean,
    modifier: Modifier = Modifier,
    error: String? = null,
    exhausted: Boolean = false,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit = onLoadMore,
) {
    val c = JmTheme.colors
    Box(
        modifier = modifier.fillMaxWidth().height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator(
                color = c.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
            )

            error != null -> TextButton(onClick = onRetry) {
                Text(
                    text = "加载失败，点击重试",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.accent,
                )
            }

            exhausted -> Text(
                text = "已经到底了",
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
            )

            else -> {
                LaunchedEffect(Unit) { onLoadMore() }
                Text(
                    text = "上滑加载更多",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                )
            }
        }
    }
}
