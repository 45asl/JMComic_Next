package com.jmcomic_next.lyqs.ui.screens.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jmcomic_next.lyqs.data.remote.dto.SeriesItem
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Radius
import com.jmcomic_next.lyqs.ui.theme.Spacing

/**
 * 章节选择器。
 *
 * 长篇动辄几百话，只靠「上一话/下一话」要翻到手酸，必须能直接跳。
 *
 * 每页 10 话，与详情页的目录一致 —— 同一份数据在两处用同一种切分方式，
 * 用户在两个界面之间切换时不需要重新适应节奏。
 *
 * 打开时**定位到当前话所在的那一页**（而不是第一页）：用户点「选择章节」时，
 * 想找的多半是当前话附近的内容。
 */
@Composable
fun ChapterPickerDialog(
    series: List<SeriesItem>,
    currentChapterId: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val c = JmTheme.colors
    val chunkSize = 10
    val pageCount = (series.size + chunkSize - 1) / chunkSize

    // 初值直接算到当前话所在的页，避免打开后先看到第一页再手动翻
    var page by remember(series, currentChapterId) {
        val index = series.indexOfFirst { it.id == currentChapterId }
        mutableIntStateOf(if (index < 0) 0 else index / chunkSize)
    }
    val safePage = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    val slice = series.drop(safePage * chunkSize).take(chunkSize)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择章节") },
        text = {
            // 滚动状态按页码取，而不是整屏共用一个：`rememberScrollState()` 会在
            // 翻页后继续沿用上一页的偏移，新一页一打开就停在底部，看起来像「这页内容缺了一半」
            val scroll = remember(safePage) { ScrollState(0) }
            Column(
                modifier = Modifier.heightIn(max = 380.dp).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                slice.forEachIndexed { index, chapter ->
                    val number = safePage * chunkSize + index + 1
                    val isCurrent = chapter.id == currentChapterId
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.xs),
                        // 当前话用强调色底标出：翻页后一眼能找到「我在哪」
                        color = if (isCurrent) c.accentSoft else c.surface1,
                        onClick = { onPick(chapter.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = chapter.sort ?: number.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isCurrent) c.accent else c.textSecondary,
                                modifier = Modifier.padding(end = Spacing.md),
                            )
                            Text(
                                text = chapter.name?.takeIf { it.isNotBlank() } ?: "第 $number 话",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isCurrent) c.accent else c.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { page = (safePage - 1).coerceAtLeast(0) },
                    enabled = safePage > 0,
                ) {
                    Icon(
                        Icons.Filled.ChevronLeft,
                        contentDescription = "上一页",
                        tint = if (safePage > 0) c.accent else c.textTertiary,
                    )
                }
                Text(
                    text = "${safePage + 1} / $pageCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary,
                )
                IconButton(
                    onClick = { page = (safePage + 1).coerceAtMost(pageCount - 1) },
                    enabled = safePage < pageCount - 1,
                ) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "下一页",
                        tint = if (safePage < pageCount - 1) c.accent else c.textTertiary,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = c.textSecondary) }
        },
    )
}
