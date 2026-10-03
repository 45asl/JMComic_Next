package com.jmcomic_next.lyqs.ui.screens.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.jmcomic_next.lyqs.BuildConfig
import com.jmcomic_next.lyqs.LiteFeatures
import com.jmcomic_next.lyqs.data.UpdateCheck
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.components.GlassLevel
import com.jmcomic_next.lyqs.ui.components.GlassSurface
import com.jmcomic_next.lyqs.ui.components.GlassTopBar
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Radius
import com.jmcomic_next.lyqs.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

/**
 * 「关于」页（1.8.0）。
 *
 * 参考 HyperCeiler 的关于页结构（版本信息 → 项目说明 → 链接 → 致谢），
 * 但内容按本项目的实际情况组织：这里最需要说清的是**变体**（full / lite、是否 debug），
 * 因为四个变体的版本号只差一个后缀，用户容易分不清自己装的是哪个。
 *
 * ## 检查更新为什么要自己发请求
 *
 * 应用的业务请求都走 `JmRepository` 的客户端（基址是 JM 的接口主机），
 * 而更新信息在 GitHub —— **不同主机**，所以这里直接用仓库那个 OkHttp
 * （复用连接池与超时设置，也不新建客户端）。
 *
 * 判定逻辑在 [UpdateCheck] 里（纯函数、有测试）；这里只负责取 `tag_name` 与呈现。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = JmTheme.colors
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var updatable by remember { mutableStateOf<String?>(null) }

    val variant = buildString {
        append(if (LiteFeatures.ENABLED) "lite" else "full")
        if (BuildConfig.DEBUG) append(" · debug")
    }

    Column(modifier.fillMaxSize()) {
        GlassTopBar(
            title = "关于",
            navigation = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = c.accent)
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            InfoCard("版本") {
                InfoRow("版本号", BuildConfig.VERSION_NAME)
                InfoRow("变体", variant)
                if (LiteFeatures.ENABLED) {
                    Text(
                        text = "lite 面向低性能设备：只保留两种纯色界面、去掉壁纸与模糊，" +
                            "并关闭图片淡入、条目动画，阅读器预取也更保守。",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary,
                    )
                }
            }

            InfoCard("检查更新") {
                Text(
                    text = "从 GitHub Releases 读取最新版本号，与本机版本比较。" +
                        "只发一个请求，不上传任何信息。",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    TextButton(
                        enabled = !checking,
                        onClick = {
                            checking = true
                            result = null
                            updatable = null
                            scope.launch {
                                val tag = runCatching { fetchLatestTag(repo.okHttp) }.getOrNull()
                                checking = false
                                result = when {
                                    tag == null -> "没取到更新信息（网络不通，或 GitHub 暂时不可用）"
                                    UpdateCheck.isNewer(tag, BuildConfig.VERSION_NAME) ->
                                        "有新版本：$tag".also { updatable = tag }
                                    else -> "已是最新版本（远端 $tag）"
                                }
                            }
                        },
                    ) { Text(if (checking) "检查中…" else "检查更新") }

                    result?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
                updatable?.let { tag ->
                    TextButton(onClick = { uri.openUri(UpdateCheck.releaseUrl(tag)) }) { Text("打开发布页") }
                }
            }

            InfoCard("说明") {
                Text(
                    text = "JMComic_Next 是禁漫（JMComic）的第三方客户端，用 Kotlin 与 Jetpack Compose 重写。" +
                        "界面风格中的 WindowGlass 与 Translucent 令牌移植自 moyingyilang.github.io 的 global.css" +
                        "（三径向 + 一线性的渐变底、发丝描边、上缘高光）；Miuix 与 Material 是另外两套表面工艺。",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }

            InfoCard("链接") {
                LinkRow("项目主页") { uri.openUri("https://github.com/${UpdateCheck.REPO}") }
                LinkRow("发布页（下载新版本）") { uri.openUri("https://github.com/${UpdateCheck.REPO}/releases") }
                LinkRow("问题反馈") { uri.openUri("https://github.com/${UpdateCheck.REPO}/issues") }
            }

            InfoCard("许可") {
                Text(
                    text = "本项目以 AGPL-3.0-only 授权。禁漫、JMComic 及相关内容的权利归其权利人所有；" +
                        "本应用与官方无关，仅提供客户端实现。",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                )
            }
        }
    }
}

/** 从 GitHub 的 `releases/latest` 里取 `tag_name`；失败返回 null。 */
private suspend fun fetchLatestTag(client: okhttp3.OkHttpClient): String? = withContext(Dispatchers.IO) {
    val req = Request.Builder()
        .url(UpdateCheck.LATEST_RELEASE_API)
        .header("Accept", "application/vnd.github+json")
        .build()
    runCatching {
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body.string()
            Json.parseToJsonElement(body).jsonObject["tag_name"]?.jsonPrimitive?.content
        }
    }.getOrNull()
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    val c = JmTheme.colors
    GlassSurface(level = GlassLevel.Card, shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.lg)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = c.text)
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = JmTheme.colors.textSecondary)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = JmTheme.colors.text)
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text = label, modifier = Modifier.fillMaxWidth())
    }
}
