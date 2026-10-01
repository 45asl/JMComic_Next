package com.jmcomic_next.lyqs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.jmcomic_next.lyqs.data.remote.dto.ListItem
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Radius
import com.jmcomic_next.lyqs.ui.theme.Spacing

/** 封面比例固定 3:4 —— 与服务端 `_3x4` 裁切一致，占位与实图之间不会跳变。 */
private const val COVER_RATIO = 3f / 4f

/**
 * 竖版漫画卡片：封面 + 标题 + 作者。用于首页推荐区的横向滚动。
 *
 * 整卡可点（而不是只有封面可点）—— 拇指操作时文字区域往往更好命中。
 */
@Composable
fun ComicCard(
    item: ListItem,
    coverUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 108.dp,
) {
    val c = JmTheme.colors
    Column(
        modifier = modifier
            .width(width)
            .clip(RoundedCornerShape(Radius.md))
            .clickable(onClick = onClick)
            .padding(bottom = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Cover(
            url = coverUrl,
            contentDescription = item.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_RATIO)
                .clip(RoundedCornerShape(Radius.md)),
        )
        Text(
            text = item.name.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = c.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.xxs),
        )
        val sub = item.author ?: item.category?.title
        if (!sub.isNullOrBlank()) {
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Spacing.xxs),
            )
        }
    }
}

/**
 * 横向漫画条目：左封面右文字。用于纵向列表（最新、搜索结果）。
 */
@Composable
fun ComicRow(
    item: ListItem,
    coverUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 尾部操作槽，例如历史列表的删除按钮。为空时布局与原来一致。 */
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = JmTheme.colors
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        level = GlassLevel.Card,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(
                url = coverUrl,
                contentDescription = item.name,
                modifier = Modifier
                    .width(64.dp)
                    .aspectRatio(COVER_RATIO)
                    .clip(RoundedCornerShape(Radius.sm)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(
                    text = item.name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = c.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                item.category?.title?.takeIf { it.isNotBlank() }?.let { CategoryChip(it) }
            }
            trailing?.invoke()
        }
    }
}

/** 分类小标签。[onClick] 非空时可点（详情页的标签用它跳到同标签搜索）。 */
@Composable
fun CategoryChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val c = JmTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.xs))
            .background(c.accentSoft)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = c.accent,
            maxLines = 1,
        )
    }
}

/**
 * 封面图。
 *
 * 用 [SubcomposeAsyncImage] 而不是 `AsyncImage`：首页同时有几十张图，
 * 加载中与失败态若不给统一占位，滚动时会显得很乱。
 * 占位与失败态都填满外部传入的尺寸，因此不会引起布局跳动。
 */
@Composable
private fun Cover(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = { CoverFallback() },
        error = { CoverFallback(showIcon = true) },
    )
}

@Composable
private fun CoverFallback(showIcon: Boolean = false) {
    val c = JmTheme.colors
    Box(
        modifier = Modifier.fillMaxSize().background(c.surfaceSunken),
        contentAlignment = Alignment.Center,
    ) {
        if (showIcon) {
            Icon(
                imageVector = Icons.Filled.BrokenImage,
                contentDescription = null,
                tint = c.textTertiary.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
