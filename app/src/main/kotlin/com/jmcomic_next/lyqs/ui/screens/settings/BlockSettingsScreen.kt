package com.jmcomic_next.lyqs.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jmcomic_next.lyqs.data.BlockRules
import com.jmcomic_next.lyqs.data.prefs.BlockStore
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.components.GlassLevel
import com.jmcomic_next.lyqs.ui.components.GlassSurface
import com.jmcomic_next.lyqs.ui.components.GlassTopBar
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Radius
import com.jmcomic_next.lyqs.ui.theme.Spacing

/** 名单条目的长度约束：太短会误伤（一个字屏蔽掉半个站），太长基本用不上。 */
private const val MIN_LENGTH = 2
private const val MAX_LENGTH = 20

/**
 * 屏蔽设置。
 *
 * 三类名单。**页面上写清每条规则的生效范围**比「能加能删」更重要：
 * 用户需要知道列表里为什么少了东西，以及标签屏蔽为什么只在详情页提示。
 */
@Composable
fun BlockSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = LocalRepository.current
    val store = repo.blockStore
    val rules by store?.state?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(BlockRules()) }
    val c = JmTheme.colors
    var dialog by remember { mutableStateOf<BlockStore.Kind?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        GlassTopBar(
            title = "屏蔽设置",
            subtitle = "本地规则，不上传",
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            notice?.let { text ->
                item(key = "notice") {
                    Text(text = text, style = MaterialTheme.typography.labelSmall, color = c.accent)
                }
            }

            item(key = "hint") {
                GlassSurface(level = GlassLevel.Card) {
                    Text(
                        text = "命中即隐藏，匹配忽略大小写。\n" +
                            "· 关键词：作品名或作者包含它\n" +
                            "· 分类：分类或子分类等于它（例如屏蔽「同人」）\n" +
                            "· 标签：列表接口不下发标签，因此改为**在后台逐条读取作品详情**取标签，命中即从列表中隐藏（只在存在标签规则时才会请求）",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textSecondary,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }

            item(key = "words") {
                BlockSection(
                    title = "关键词屏蔽",
                    hint = "作品名或作者命中即隐藏 · ${rules.words.size} 条",
                    values = rules.words.toList(),
                    onAdd = { dialog = BlockStore.Kind.Word },
                    onRemove = { store?.removeWord(it) },
                )
            }

            item(key = "categories") {
                BlockSection(
                    title = "分类屏蔽",
                    hint = "整个分类不再出现 · ${rules.categories.size} 条",
                    values = rules.categories.toList(),
                    onAdd = { dialog = BlockStore.Kind.Category },
                    onRemove = { store?.removeCategory(it) },
                )
            }

            item(key = "tags") {
                BlockSection(
                    title = "标签屏蔽",
                    hint = "打开作品时提示 · ${rules.tags.size} 条",
                    values = rules.tags.toList(),
                    onAdd = { dialog = BlockStore.Kind.Tag },
                    onRemove = { store?.removeTag(it) },
                )
            }
        }
    }

    dialog?.let { kind ->
        AddBlockDialog(
            kind = kind,
            onDismiss = { dialog = null },
            onConfirm = { value ->
                val trimmed = value.trim()
                notice = when {
                    trimmed.length < MIN_LENGTH || trimmed.length > MAX_LENGTH ->
                        "长度应为 $MIN_LENGTH~$MAX_LENGTH 个字符"

                    store?.isBlocked(kind, trimmed) == true -> "「$trimmed」已经在名单里"

                    else -> {
                        when (kind) {
                            BlockStore.Kind.Word -> store?.addWord(trimmed)
                            BlockStore.Kind.Tag -> store?.addTag(trimmed)
                            BlockStore.Kind.Category -> store?.addCategory(trimmed)
                        }
                        "已屏蔽「$trimmed」"
                    }
                }
                dialog = null
            },
        )
    }
}

/** 一份名单：标题 + 生效范围 + 已加条目（逐条可删）+ 添加按钮。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockSection(
    title: String,
    hint: String,
    values: List<String>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val c = JmTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(hint, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "添加$title", tint = c.accent)
            }
        }

        if (values.isEmpty()) {
            Text(
                text = "还没有添加",
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        } else {
            GlassSurface(level = GlassLevel.Card) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    values.sorted().forEach { value ->
                        Surface(shape = RoundedCornerShape(Radius.pill), color = c.accentSoft) {
                            Row(
                                modifier = Modifier.padding(
                                    start = Spacing.md,
                                    end = Spacing.xs,
                                    top = Spacing.xs,
                                    bottom = Spacing.xs,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = c.accent,
                                )
                                IconButton(
                                    onClick = { onRemove(value) },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "移除 $value",
                                        tint = c.accent,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 添加一条：只负责收字，长度与重复校验由调用方处理（那里才知道名单是哪一份）。 */
@Composable
private fun AddBlockDialog(
    kind: BlockStore.Kind,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val c = JmTheme.colors
    var text by remember { mutableStateOf("") }
    val label = when (kind) {
        BlockStore.Kind.Word -> "关键词"
        BlockStore.Kind.Tag -> "标签"
        BlockStore.Kind.Category -> "分类名"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加$label") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = when (kind) {
                                BlockStore.Kind.Word -> "例如：NTR"
                                BlockStore.Kind.Tag -> "例如：纯爱"
                                BlockStore.Kind.Category -> "例如：同人"
                            },
                            color = c.textTertiary,
                        )
                    },
                )
                Text(
                    text = "$MIN_LENGTH~$MAX_LENGTH 个字符",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text("添加", color = c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = c.textSecondary) }
        },
    )
}
