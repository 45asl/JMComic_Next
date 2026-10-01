package com.jmcomic_next.lyqs.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Spacing

/** 加载中。用主题强调色，避免 Material 默认紫在博客配色里显得突兀。 */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = JmTheme.colors.accent,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(36.dp),
        )
    }
}

/**
 * 空态 / 错误态。
 *
 * 刻意做成同一个组件：对用户而言「没内容」和「拿不到内容」的呈现方式基本一致，
 * 差别只在图标、文案与是否给重试按钮。
 */
@Composable
fun MessageState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector = Icons.Filled.Inbox,
    onRetry: (() -> Unit)? = null,
) {
    val c = JmTheme.colors
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = c.textTertiary,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.md),
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        if (onRetry != null) {
            TextButton(onClick = onRetry, modifier = Modifier.padding(top = Spacing.sm)) {
                Text("重试", color = c.accent)
            }
        }
    }
}

/** 网络错误态。 */
@Composable
fun ErrorBox(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) = MessageState(
    title = "加载失败",
    description = message,
    icon = Icons.Filled.CloudOff,
    onRetry = onRetry,
    modifier = modifier,
)
