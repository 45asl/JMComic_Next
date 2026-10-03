package com.jmcomic_next.lyqs.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jmcomic_next.lyqs.data.remote.dto.NotificationItem
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.components.GlassSurface
import com.jmcomic_next.lyqs.ui.components.GlassTopBar
import com.jmcomic_next.lyqs.ui.components.GlassLevel
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Radius
import com.jmcomic_next.lyqs.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * 通知列表（1.5.3）。
 *
 * 数据全部来自**服务端**（`GET notifications`）：追更的作品更新时服务端写一条通知，
 * 站内公告也是同一套。客户端只负责展示与"标记已读"，不自己判断有没有更新 ——
 * 那一版实现（拿本地阅读时间与 `update_at` 比）是错的，已经删掉。
 *
 * 标签页与类型参数的对应关系来自源码 `NotificationList.tsx`：
 * 全部 `all` / 追更 `comic_follow` / 站内通知 `site_notice`。
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenComic: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = JmTheme.colors
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(TYPE_ALL) }
    var items_ by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // 服务端按页返回（官方界面每页 20 条），所以这里也必须翻页 ——
    // 只取第一页的话，通知一多后面的就永远看不到。
    LaunchedEffect(tab, page) {
        if (page == 1) {
            loading = true
            error = null
        }
        runCatching { repo.notifications(type = tab, page = page) }
            .onSuccess { payload ->
                // 第一页替换、后续追加：翻页时不能把已读状态丢掉
                items_ = if (page == 1) payload.list else items_ + payload.list
                total = payload.total
            }
            .onFailure {
                // 只有第一页失败才报错；追加失败时保留已显示的内容，不把整屏清掉
                if (page == 1) error = it.message?.takeIf { m -> m.isNotBlank() } ?: "网络问题"
            }
        loading = false
    }

    Column(modifier.fillMaxSize()) {
        GlassTopBar(
            title = "通知",
            navigation = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = c.accent)
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TABS.forEach { (label, value) ->
                TextButton(onClick = { tab = value; page = 1 }) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (tab == value) c.accent else c.textSecondary,
                    )
                }
            }
        }
        when {
            loading -> Hint("正在读取通知…")
            error != null -> Hint("读取失败：$error")
            items_.isEmpty() -> Hint("还没有通知。")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(items_, key = { it.idText ?: it.hashCode().toString() }) { n ->
                    NotificationCard(
                        item = n,
                        onOpenComic = onOpenComic,
                        onMarkRead = {
                            val id = n.idText ?: return@NotificationCard
                            // 先本地标已读（界面立刻变），再发请求；失败也不回滚 ——
                            // 已读是个弱状态，回滚反而会让用户看到"点过的又变未读"
                            items_ = items_.map { if (it.idText == id) it else it }
                            scope.launch { runCatching { repo.markNotificationRead(id, true) } }
                        },
                    )
                }
                if (items_.size < total) {
                    item(key = "more") {
                        // 显式的"加载更多"而不是触底自动加载：通知不是无限流，
                        // 自动加载在快速滑动时容易连翻好几页，用户也说不清现在看到哪了
                        TextButton(
                            onClick = { page += 1 },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("加载更多（已显示 ${items_.size} / $total）")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = JmTheme.colors.textSecondary,
        modifier = Modifier.padding(Spacing.lg),
    )
}

@Composable
private fun NotificationCard(
    item: NotificationItem,
    onOpenComic: (String) -> Unit,
    onMarkRead: () -> Unit,
) {
    val c = JmTheme.colors
    val updates = item.followedUpdates()
    GlassSurface(
        level = GlassLevel.Card,
        shape = RoundedCornerShape(Radius.md),
        modifier = Modifier.fillMaxWidth(),
        onClick = onMarkRead,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (!item.isRead) {
                    // 未读用一个圆点表示，而不是整行加粗 —— 加粗会让整屏都在喊
                    Box16(c.accent)
                }
                Text(
                    text = if (item.typeText == NotificationItem.TYPE_COMIC_FOLLOW) "追更更新" else (item.titleText ?: "站内通知"),
                    style = MaterialTheme.typography.titleSmall,
                    color = c.text,
                )
                item.dateText?.let {
                    Text(text = it, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                }
            }

            if (updates.isNotEmpty()) {
                updates.forEach { up ->
                    val id = up.comicIdText
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.sm))
                            .clickable(enabled = id != null) {
                                onMarkRead()
                                id?.let(onOpenComic)
                            }
                            .padding(vertical = Spacing.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = up.comicTitleText.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.accent,
                            modifier = Modifier.weight(1f),
                        )
                        up.updateDateText?.let {
                            Text(text = it, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                        }
                    }
                }
            } else {
                // 站内通知的正文是 HTML 字符串（源码里用 dangerouslySetInnerHTML 渲染）。
                // 这里先按纯文本显示：去掉标签，保证内容能读；富文本渲染留待以后。
                val body = item.siteNoticeHtml()?.replace(Regex("<[^>]*>"), "")?.trim()
                if (!body.isNullOrBlank()) {
                    Text(text = body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun Box16(color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.size(8.dp).clip(CircleShape).background(color),
    )
}

private const val TYPE_ALL = "all"
private val TABS = listOf(
    "全部" to TYPE_ALL,
    "追更" to NotificationItem.TYPE_COMIC_FOLLOW,
    "站内通知" to NotificationItem.TYPE_SITE_NOTICE,
)
