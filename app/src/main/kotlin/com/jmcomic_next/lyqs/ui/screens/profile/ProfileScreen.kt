package com.jmcomic_next.lyqs.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jmcomic_next.lyqs.BuildConfig
import com.jmcomic_next.lyqs.data.prefs.ThemeMode
import com.jmcomic_next.lyqs.data.remote.AdBlocker
import com.jmcomic_next.lyqs.ui.components.GlassLevel
import com.jmcomic_next.lyqs.ui.components.GlassSurface
import com.jmcomic_next.lyqs.ui.components.GlassTopBar
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Motion
import com.jmcomic_next.lyqs.ui.theme.Spacing

/**
 * 「我的」页：只有外观设置与关于信息。
 *
 * 刻意不做账号体系 —— 源码里的登录/注册/收藏/观看历史都依赖服务端账号，
 * 而本应用的定位是只读浏览，把凭据相关的东西做进来只会扩大攻击面。
 */
@Composable
fun ProfileScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        GlassTopBar(title = "我的")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { AppearanceCard(themeMode, onThemeModeChange, dynamicColor, onDynamicColorChange) }
            item { PrivacyCard() }
            item { AboutCard() }
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

/**
 * 隐私与广告。
 *
 * 这一栏存在的意义是把「无广告」从一句承诺变成界面上看得见的事实：
 * 说明广告在官方客户端里从哪来（两个接口 + 60 多个插槽），
 * 以及本应用为什么不会出现它们。
 */
@Composable
private fun PrivacyCard() {
    val c = JmTheme.colors
    SettingCard(title = "隐私与广告") {
        InfoRow("广告接口调用", "从不调用")
        InfoRow("已屏蔽广告/追踪域名", "${AdBlocker.blockedDomainCount} 类")
        Text(
            text = "官方客户端的广告全部由客户端主动请求广告接口后自行插入，" +
                "官方代码里定义了 60 多个插槽位置。本应用不实现任何插槽、不请求广告接口，" +
                "并在网络层屏蔽第三方广告与追踪域名；图片通道共用同一个客户端，因此同样受拦截。" +
                "另外不做任何行为采集。",
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
