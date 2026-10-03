package com.jmcomic_next.lyqs.ui.screens.profile

import androidx.compose.foundation.layout.Box
import coil3.compose.AsyncImage
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.remote.dto.DailyHistory
import com.jmcomic_next.lyqs.data.remote.dto.DailyHistoryOptions
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Radius
import com.jmcomic_next.lyqs.ui.theme.Spacing

/**
 * 签到**历史日历**（1.5.4，对应源码 `DailyList.tsx`）。
 *
 * 与"今天打卡"是两件事：先取可选的年份（`daily_list`），再取那一年的记录
 * （`daily_list/filter`）—— 每条带一张图，点开看大图。
 *
 * 默认**不请求**：这一块要点开才加载（`expanded`），因为多数人进来只是打卡，
 * 不该为了一块没看的历史多打两次接口。
 */
@Composable
fun DailyHistorySection(
    repo: JmRepository,
    uid: String,
    modifier: Modifier = Modifier,
) {
    val c = JmTheme.colors
    var expanded by remember { mutableStateOf(false) }
    var years by remember { mutableStateOf<List<String>>(emptyList()) }
    var year by remember { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf<List<DailyHistory.Entry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // 展开时才拉年份；换账号（uid 变）要重新拉，否则会把上一个账号的年份显示出来
    LaunchedEffect(expanded, uid) {
        if (!expanded) return@LaunchedEffect
        if (years.isNotEmpty()) return@LaunchedEffect
        loading = true
        error = null
        runCatching { repo.dailyHistoryOptions(uid) }
            .onSuccess { options: DailyHistoryOptions ->
                years = options.list.mapNotNull { it.titleText }
                // 默认选第一个（服务端按从新到旧给，第一个就是最新那年）
                year = years.firstOrNull()
            }
            .onFailure { error = it.message?.takeIf { m -> m.isNotBlank() } ?: "网络问题" }
        loading = false
    }

    // 选定年份后再拉那一年的记录
    LaunchedEffect(year) {
        val y = year ?: return@LaunchedEffect
        loading = true
        error = null
        runCatching { repo.dailyHistory(y) }
            .onSuccess { entries = it.list }
            .onFailure { error = it.message?.takeIf { m -> m.isNotBlank() } ?: "网络问题" }
        loading = false
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (expanded) "收起签到历史" else "查看签到历史",
                style = MaterialTheme.typography.bodySmall,
                color = c.accent,
                modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = Spacing.xs),
            )
            if (loading) {
                Text(
                    text = "读取中…",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        }

        if (!expanded) return@Column

        error?.let {
            Text(
                text = "历史没读到：$it",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
            return@Column
        }

        if (years.isEmpty() && !loading) {
            Text(
                text = "还没有签到历史。",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
            return@Column
        }

        // 年份筛选：横向一排，选中的用强调色标出
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(years, key = { it }) { y ->
                val selected = y == year
                Text(
                    text = y,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) c.textOnAccent else c.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(if (selected) c.accent else c.surface2)
                        .clickable { year = y; preview = null }
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
        }

        // 该年的记录：一条一张图，点一下在下面看大图
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(entries, key = { it.imgText ?: it.hashCode().toString() }) { entry ->
                val url = entry.imgText ?: return@items
                Box {
                    AsyncImage(
                        model = url,
                        contentDescription = entry.dateText ?: "签到记录",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(Radius.sm))
                            .clickable { preview = url },
                    )
                    // 月份角标：源码在缩略图左上角写"N月"，这样不点开也知道是哪个月的
                    entry.monthText?.let { m ->
                        Text(
                            text = "${m}月",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textOnAccent,
                            modifier = Modifier
                                .padding(2.dp)
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(c.accent)
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }

        preview?.let { url ->
            // 就地放大而不是弹窗：少一层返回栈，也不会挡住卡片本身的信息
            AsyncImage(
                model = url,
                contentDescription = "签到大图",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(Radius.md)),
            )
        }
    }
}
