package com.jmcomic_next.lyqs.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jmcomic_next.lyqs.BuildConfig
import com.jmcomic_next.lyqs.data.prefs.ThemeMode
import com.jmcomic_next.lyqs.data.remote.AdBlocker
import com.jmcomic_next.lyqs.data.remote.JmSession
import com.jmcomic_next.lyqs.data.remote.dto.JmSettings
import com.jmcomic_next.lyqs.data.remote.dto.MemberInfo
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.components.CategoryChip
import com.jmcomic_next.lyqs.ui.components.GlassLevel
import com.jmcomic_next.lyqs.ui.components.GlassSurface
import com.jmcomic_next.lyqs.ui.components.GlassTopBar
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Motion
import com.jmcomic_next.lyqs.ui.theme.Spacing

/**
 * 「我的」页。
 *
 * 账号卡在顶部：未登录时只有一个登录入口，登录后展示会员信息并提供收藏/历史的入口。
 * 登录态来自 [com.jmcomic_next.lyqs.data.auth.AuthStore] 的可观察状态，
 * 因此服务端拒绝凭证导致的被动登出会立刻反映到这里。
 */
@Composable
fun ProfileScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = LocalRepository.current
    val auth by repo.auth.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        GlassTopBar(title = "我的")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                AccountCard(
                    loggedIn = auth.loggedIn,
                    member = auth.member,
                    onLogin = onLogin,
                    onLogout = onLogout,
                    onOpenFavorites = onOpenFavorites,
                    onOpenHistory = onOpenHistory,
                )
            }
            item { AppearanceCard(themeMode, onThemeModeChange, dynamicColor, onDynamicColorChange) }
            item { PrivacyCard() }
            item { AboutCard() }
            item { ServerCard() }
        }
    }
}

@Composable
private fun AccountCard(
    loggedIn: Boolean,
    member: MemberInfo?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val c = JmTheme.colors
    SettingCard(title = "账号") {
        if (!loggedIn) {
            Text(
                text = "未登录",
                style = MaterialTheme.typography.bodyLarge,
                color = c.text,
            )
            Text(
                text = "收藏与观看历史绑定账号，登录后可在本机同步查看。",
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
            TextButton(onClick = onLogin) {
                Text("登录 / 注册", color = c.accent)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member?.displayName ?: "已登录",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.text,
                    modifier = Modifier.weight(1f),
                )
                member?.levelName?.takeIf { it.isNotBlank() }?.let { CategoryChip(it) }
            }

            member?.let { info ->
                if (info.coin != null) InfoRow("金币", info.coin)
                InfoRow("等级", info.level.toString())
                // 官方此字段表示免广告会员；本应用本身无广告，这里只作为会员状态展示
                InfoRow("免广告特权", if (info.adFree) "已开通" else "未开通")
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                EntryButton("我的收藏", Icons.Filled.BookmarkBorder, onOpenFavorites)
                EntryButton("观看历史", Icons.Filled.History, onOpenHistory)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Logout,
                    contentDescription = null,
                    tint = c.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
                TextButton(onClick = onLogout) {
                    Text("退出登录", color = c.textSecondary)
                }
            }
        }
    }
}

// 声明为 RowScope 扩展：这样函数体内的 Modifier.weight 才在作用域内
@Composable
private fun RowScope.EntryButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    val c = JmTheme.colors
    GlassSurface(
        modifier = Modifier.weight(1f),
        level = GlassLevel.Card,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = c.accent,
                modifier = Modifier.size(18.dp),
            )
            Text(label, style = MaterialTheme.typography.bodyMedium, color = c.text)
        }
    }
}

@Composable
private fun AppearanceCard(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    val c = JmTheme.colors
    SettingCard(title = "外观") {
        Text("主题", style = MaterialTheme.typography.bodyLarge, color = c.text)
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        ) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                ) {
                    Text(
                        text = when (mode) {
                            ThemeMode.System -> "跟随系统"
                            ThemeMode.Light -> "浅色"
                            ThemeMode.Dark -> "深色"
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = Spacing.md)) {
                Text("动态取色", style = MaterialTheme.typography.bodyLarge, color = c.text)
                Text(
                    text = "取系统壁纸主色（Android 12+）。只会替换强调色，玻璃层次仍按本站配色。",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                )
            }
            Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
        }
    }
}

@Composable
private fun PrivacyCard() {
    val c = JmTheme.colors
    SettingCard(title = "隐私与广告") {
        InfoRow("广告接口调用", "从不调用")
        InfoRow("已屏蔽广告/追踪域名", "${AdBlocker.blockedDomainCount} 类")
        InfoRow("凭证存储", "Keystore 加密")
        Text(
            text = "官方客户端的广告全部由客户端主动请求广告接口后自行插入，" +
                "官方代码里定义了 60 多个插槽位置。本应用不实现任何插槽、不请求广告接口，" +
                "并在网络层屏蔽第三方广告与追踪域名；图片通道共用同一个客户端，因此同样受拦截。" +
                "另外不做任何行为采集。登录凭证经 Android Keystore 加密后落盘，不保存明文。",
            style = MaterialTheme.typography.labelSmall,
            color = c.textTertiary,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

@Composable
private fun AboutCard() {
    val c = JmTheme.colors
    SettingCard(title = "关于") {
        InfoRow("版本", BuildConfig.VERSION_NAME)
        InfoRow("设计系统", "Fluent (Acrylic/Mica) × MIUI 毛玻璃")
        InfoRow("动效基准", "${Motion.FAST} / ${Motion.BASE} / ${Motion.SLOW} ms")
        Text(
            text = "视觉令牌移植自 moyingyilang.github.io 的 global.css。" +
                "本应用不提供壁纸功能，毛玻璃采样自内置的环境渐变底。",
            style = MaterialTheme.typography.labelSmall,
            color = c.textTertiary,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

/**
 * 服务端信息与协议对齐状态。
 *
 * 这里刻意**不做成「检查更新」**：服务端回的 `jm3_version` 是官方 App 的版本，
 * 与本应用的版本没有可比性，拿它提示「有新版本」是误导。
 *
 * 真正有用的是它作为**协议对齐探针**：本应用在 `Tokenparam` 里上报的版本是照官方版本填的
 * （见 `JmSession.DEFAULT_CLIENT_VERSION`），一旦这里与服务端不一致，
 * 说明服务端已在面向新的客户端行为，当前实现可能需要跟进。
 */
@Composable
private fun ServerCard() {
    val repo = LocalRepository.current
    val c = JmTheme.colors
    var settings by remember { mutableStateOf<JmSettings?>(null) }
    var checked by remember { mutableStateOf(false) }
    // 用递增的 key 驱动重读：LaunchedEffect(Unit) 只会跑一次，
    // 若只把状态清空而不换 key，按钮点了不会有任何反应
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        checked = false
        settings = runCatching {
            repo.bootstrap()
            repo.settings()
        }.getOrNull()
        checked = true
    }

    val serverVersion = settings?.jm3Version?.takeIf { it.isNotBlank() }
    val clientVersion = JmSession.DEFAULT_CLIENT_VERSION
    val aligned = serverVersion == null || serverVersion == clientVersion

    SettingCard(title = "服务端") {
        InfoRow("服务端对应官方版本", serverVersion ?: if (checked) "未提供" else "读取中…")
        InfoRow("本客户端上报版本", clientVersion)
        InfoRow("协议对齐", if (aligned) "一致" else "可能已变化")

        if (!aligned) {
            Text(
                text = "服务端对应的官方版本已变为 $serverVersion，而本客户端仍按 $clientVersion 上报。" +
                    "这不代表立即不可用，但接口行为可能已按新版本调整，值得检查。",
                style = MaterialTheme.typography.labelSmall,
                color = c.error,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        settings?.jm3VersionInfo?.takeIf { it.isNotBlank() }?.let { info ->
            Text(
                text = info,
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        TextButton(onClick = { reloadKey++ }) {
            Text("重新读取", color = c.accent)
        }
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = JmTheme.colors
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        level = GlassLevel.Card,
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = c.text,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val c = JmTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = c.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = c.text)
    }
}
