package com.jmcomic_next.lyqs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.jmcomic_next.lyqs.ui.jmSharedElement
import com.jmcomic_next.lyqs.data.remote.dto.ListItem
import com.jmcomic_next.lyqs.ui.theme.jmShape
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Radius
import com.jmcomic_next.lyqs.ui.theme.Spacing

/** 封面比例固定 3:4 —— 与服务端 `_3x4` 裁切一致，占位与实图之间不会跳变。 */
private const val COVER_RATIO = 3f / 4f

/**
 * 卡片尺寸。
 *
 * 原来横向行用 108dp：360dp 宽的屏上正好排三张，封面只有拇指盖大，标题挤成两行 11.5sp，
 * 整屏看下来是「很多很小的方块」。改成 132dp 后一行露出约 2.5 张（第三张切一半），
 * 既是常见的封面流节奏，也让封面大到能看清画面。
 *
 * 网格则用 [grid]：两列、封面约 150dp。
 */
object CardSizes {
    /** 首页推荐区横向滚动的卡片宽度 */
    val row: Dp = 132.dp

    /** 网格（分类 / 搜索结果格 / 分区更多 / 周刊 / 画师作品）的单格最小宽度 */
    val grid: Dp = 148.dp
}

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
    width: Dp = CardSizes.row,
    /**
     * 共享元素键：给了值，封面就会在导航前后**沿它前后两个位置插值**
     * （列表里的封面矩形 → 详情页的封面矩形），而不是跟页面一起被淡掉。
     * 见 [com.jmcomic_next.lyqs.ui.jmSharedElement]。
     */
    sharedKey: String? = null,
) {
    val c = JmTheme.colors
    Column(
        modifier = modifier
            .width(width)
            .clip(jmShape(Radius.lg))
            .clickable(onClick = onClick)
            .padding(bottom = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Cover(
            url = coverUrl,
            contentDescription = item.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_RATIO)
                .jmSharedElement(sharedKey)
                .clip(jmShape(Radius.lg)),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                text = item.name.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = c.text,
                // 固定两行：标题有一行有两行的话，下面的作者行会跟着上下跳，
                // 一整排卡片看起来就是参差不齐的（真机上实测行高 27/33/75px 三种）。
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // 作者行同样占位：没有作者时留空也不塌陷，保持同一排卡片等高
            Text(
                text = item.author ?: item.category?.title.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(
                url = coverUrl,
                contentDescription = item.name,
                modifier = Modifier
                    .width(76.dp)
                    .aspectRatio(COVER_RATIO)
                    .clip(jmShape(Radius.md)),
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
    /** 长按动作。详情页用「点=搜索，长按=屏蔽」，避免为屏蔽再塞一排按钮。 */
    onLongClick: (() -> Unit)? = null,
    /** 已被屏蔽：弱化显示并画删除线，一眼能看出「这条规则已生效」。 */
    blocked: Boolean = false,
) {
    val c = JmTheme.colors
    Box(
        modifier = modifier
            .clip(jmShape(Radius.xs))
            .background(if (blocked) c.surfaceSunken else c.accentSoft)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (blocked) c.textTertiary else c.accent,
            textDecoration = if (blocked) TextDecoration.LineThrough else null,
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
