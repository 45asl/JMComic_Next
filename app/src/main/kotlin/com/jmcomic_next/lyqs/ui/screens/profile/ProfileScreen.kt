package com.jmcomic_next.lyqs.ui.screens.profile

import androidx.compose.material3.SwitchDefaults
import com.jmcomic_next.lyqs.LiteFeatures
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.produceState
import com.jmcomic_next.lyqs.data.remote.dto.DailyPayload
import com.jmcomic_next.lyqs.data.Daily
import com.jmcomic_next.lyqs.JmApp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Button
import com.jmcomic_next.lyqs.data.prefs.AppPrefs
import com.jmcomic_next.lyqs.data.SerialNotify
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import com.jmcomic_next.lyqs.ui.LocalBottomBarInset
import com.jmcomic_next.lyqs.BuildConfig
import com.jmcomic_next.lyqs.data.BlockRules
import com.jmcomic_next.lyqs.data.wallpaper.WallpaperMode
import com.jmcomic_next.lyqs.data.prefs.ReaderMode
import com.jmcomic_next.lyqs.data.prefs.ThemeMode
import com.jmcomic_next.lyqs.data.remote.AdBlocker
import com.jmcomic_next.lyqs.data.remote.JmSession
import com.jmcomic_next.lyqs.data.remote.dto.JmSettings
import com.jmcomic_next.lyqs.data.remote.dto.MemberInfo
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.LocalWallpaper
import com.jmcomic_next.lyqs.ui.LocalWallpaperStore
import com.jmcomic_next.lyqs.ui.components.CategoryChip
import com.jmcomic_next.lyqs.ui.components.GlassLevel
import com.jmcomic_next.lyqs.ui.components.GlassSurface
import com.jmcomic_next.lyqs.ui.components.GlassTopBar
import com.jmcomic_next.lyqs.ui.theme.jmShape
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.LocalJmPalette
import com.jmcomic_next.lyqs.ui.theme.LocalJmSpec
import com.jmcomic_next.lyqs.ui.theme.Motion
import com.jmcomic_next.lyqs.ui.theme.Radius
import com.jmcomic_next.lyqs.ui.theme.Spacing
import com.jmcomic_next.lyqs.ui.theme.Styles
import com.jmcomic_next.lyqs.ui.UiOptions
import com.jmcomic_next.lyqs.ui.theme.MotionStyle
import com.jmcomic_next.lyqs.ui.theme.ThemeStyle
import com.jmcomic_next.lyqs.ui.theme.paletteFor


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
    readerMode: ReaderMode,
    onReaderModeChange: (ReaderMode) -> Unit,
    themeStyle: ThemeStyle,
    onThemeStyleChange: (ThemeStyle) -> Unit,
    isDark: Boolean,
    uiOptions: UiOptions,
    onUiOptionsChange: (UiOptions) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTracking: () -> Unit,
    onOpenNotifications: () -> Unit,
    /** 打开「关于」整页（1.8.0）。 */
    onOpenAbout: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenBlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = LocalRepository.current
    val auth by repo.auth.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        GlassTopBar(title = "我的")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.lg,
                // 悬浮底栏压在上面，底部要把它的高度留出来
                bottom = Spacing.lg + LocalBottomBarInset.current,
            ),
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
                    onOpenTracking = onOpenTracking,
                    onOpenNotifications = onOpenNotifications,
                    onOpenTags = onOpenTags,
                )
            }
            item { DailyCard(onLogin = onLogin) }
            item {
                AppearanceCard(
                    themeStyle = themeStyle,
                    onThemeStyleChange = onThemeStyleChange,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    dynamicColor = dynamicColor,
                    onDynamicColorChange = onDynamicColorChange,
                    isDark = isDark,
                    uiOptions = uiOptions,
                    onUiOptionsChange = onUiOptionsChange,
                )
            }
            // lite 去掉了全部壁纸与模糊功能，这一整张卡就不该出现
            if (!LiteFeatures.ENABLED) item { WallpaperCard() }
            item { BlockCard(onOpenBlock) }
            item { ReadingCard(readerMode, onReaderModeChange) }
            item { PrivacyCard() }
            item { AboutCard(onOpenAbout = onOpenAbout) }
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
    onOpenTracking: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenTags: () -> Unit,
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
                // DTO 搬到 :shared 之后，跨模块的公开 val 属性不能再被智能转换，
                // 必须先取到局部变量（编译器会报 SMARTCAST_IMPOSSIBLE）
                val coin = info.coin
                if (coin != null) InfoRow("金币", coin)
                InfoRow("等级", info.level.toString())
                // 官方此字段表示免广告会员；本应用本身无广告，这里只作为会员状态展示
                InfoRow("免广告特权", if (info.adFree) "已开通" else "未开通")
            }

            // 四个入口**分成两行两列**，而不是挤在一行里按 weight 平分：
            // 每行平分后每格约 160dp，足够放下图标 + 四个汉字；
            // 一行四个的话在 360dp 宽的屏上每格只剩不到 80dp，「观看历史」会被截断。
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                EntryButton("我的收藏", Icons.Filled.BookmarkBorder, onOpenFavorites)
                EntryButton("观看历史", Icons.Filled.History, onOpenHistory)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                // 追更与标签收藏都在服务端，且都有上限（追更 500 / 标签 50）
                // 未读通知数：服务端在追更作品更新时生成通知，这里只镜像数量。
                // 未登录时不请求（接口要账号），也就不显示角标。
                val repoForUnread = LocalRepository.current
                val loggedInForUnread = repoForUnread.auth.isLoggedIn
                val unread by produceState(initialValue = 0, loggedInForUnread) {
                    value = if (!loggedInForUnread) {
                        0
                    } else {
                        runCatching { repoForUnread.notificationsUnread().total }.getOrDefault(0)
                    }
                }
                // 未读角标挂在"通知"上（语义属于通知），"我的追更"保持干净：
                // 两个入口都带同一个数字会让人以为是两件不同的事
                EntryButton("通知", Icons.Filled.Notifications, onOpenNotifications, badge = unread)
                EntryButton("我的追更", Icons.Filled.NotificationsNone, onOpenTracking)
                EntryButton("标签收藏", Icons.Filled.BookmarkAdd, onOpenTags)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
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
private fun RowScope.EntryButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    /** 未读计数；>0 时在标签右侧显示小胶囊。 */
    badge: Int = 0,
) {
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
            if (badge > 0) {
                // 与分区标题旁的"更新"计数同一套语言（强调色胶囊），用户不用学第二套标记
                Text(
                    text = if (badge > 99) "99+" else badge.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textOnAccent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(c.accent)
                        .padding(horizontal = Spacing.xs, vertical = 1.dp),
                )
            }
        }
    }
}

/**
 * 内容屏蔽。
 *
 * 三份名单存在本地，命中即隐藏 —— 屏蔽只作用于本机，不影响账号，也不影响官方客户端。
 * 这里只做汇总与入口，增删都在 [com.jmcomic_next.lyqs.ui.screens.settings.BlockSettingsScreen]。
 */
@Composable
private fun BlockCard(onOpenBlock: () -> Unit) {
    val c = JmTheme.colors
    val store = LocalRepository.current.blockStore
    val rules by store?.state?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(BlockRules()) }
    val total = rules.words.size + rules.categories.size + rules.tags.size

    SettingCard(title = "内容屏蔽") {
        InfoRow("关键词", "${rules.words.size} 条")
        InfoRow("分类", "${rules.categories.size} 条")
        InfoRow("标签", "${rules.tags.size} 条")
        Text(
            text = if (total == 0) {
                "还没有屏蔽任何内容。可以按作品名、作者、分类或标签屏蔽，命中即从列表中隐藏。"
            } else {
                "已生效 $total 条规则。列表里看不到的作品可能正是被它们隐藏的。"
            },
            style = MaterialTheme.typography.labelSmall,
            color = c.textTertiary,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        GlassSurface(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            level = GlassLevel.Card,
            onClick = onOpenBlock,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "管理屏蔽名单",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.text,
                )
            }
        }
    }
}

/**
 * 外观。
 *
 * 顺序刻意是「先风格、后深浅」：风格决定的是**这套界面像谁**（圆角尺度、表面工艺、
 * 字重、动效），深浅色只是在它之上的一维。先选颜色的界面，用户会以为自己在挑主题包。
 */
@Composable
private fun AppearanceCard(
    themeStyle: ThemeStyle,
    onThemeStyleChange: (ThemeStyle) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    isDark: Boolean,
    uiOptions: UiOptions,
    onUiOptionsChange: (UiOptions) -> Unit,
) {
    val c = JmTheme.colors
    SettingCard(title = "外观") {
        Text("风格", style = MaterialTheme.typography.bodyLarge, color = c.text)
        Text(
            text = themeStyle.tagline,
            style = MaterialTheme.typography.labelSmall,
            color = c.textTertiary,
            modifier = Modifier.padding(top = Spacing.xxs),
        )

        // 预览卡：**用该风格自己的令牌渲染**，所以预览就是它真实的样子，
        // 不是画一张示意图（图会跟实现走散，这种「示意图撒谎」的问题很难被发现）
        // lite 下只列出真正可用的两种纯色风格：另外三种的规格定义已被 R8 删除，
        // 列出来会让用户选到一个不存在的风格（看起来像"选了没用"）
        val availableStyles = if (LiteFeatures.ENABLED) {
            listOf(ThemeStyle.Miuix, ThemeStyle.Material)
        } else {
            ThemeStyle.entries
        }
        availableStyles.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                row.forEach { style ->
                    StyleOption(
                        style = style,
                        selected = style == themeStyle,
                        isDark = isDark,
                        onClick = { onThemeStyleChange(style) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // 五套风格是奇数，最后一行只有一张卡；补一个等宽占位，
                // 否则那张卡会被 weight 拉成整行宽，比上面几张明显大一圈
                if (row.size < 2) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        Text(
            text = "主题",
            style = MaterialTheme.typography.bodyLarge,
            color = c.text,
            modifier = Modifier.padding(top = Spacing.lg),
        )
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
                    text = "取系统壁纸的配色（Android 12+）。Material 风格会整份跟随系统；" +
                        "另外几套玻璃只取强调色，层次仍按本站配色。",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                )
            }
            Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
        }
        // ---- 1.5.3 连载更新提醒：**可选、默认关** ----
        //
        // 开关状态直接读写 prefs（而不是像 uiOptions 那样从上层传下来）：
        // 它只影响这一个后台闹钟，不参与主题/界面组合，多绕三层参数不值得。
        val notifyContext = LocalContext.current
        val notifyPrefs = remember(notifyContext) { AppPrefs(notifyContext) }
        var serialNotify by remember { mutableStateOf(notifyPrefs.serialNotify) }
        val askNotifyPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            // 用户拒绝权限时**不把开关打开**：否则会出现"开关是开的、但从来不响"，
            // 那种状态比干脆给不了这个功能更让人困惑
            serialNotify = granted
            notifyPrefs.serialNotify = granted
            if (granted) SerialNotify.enable(notifyContext) else SerialNotify.disable(notifyContext)
        }

        SettingCard(title = "连载更新提醒") {
            OptionSwitch(
                title = "你追的连载更新时通知我",
                desc = "每半天左右让系统挑个合适时机检查一次，有变化才提醒；同一批更新只提醒一次。" +
                    "只用连载列表和你本地的阅读时间，不上传你在看什么。默认关闭。",
                checked = serialNotify,
                onCheckedChange = { want ->
                    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            notifyContext,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    when {
                        !want -> {
                            serialNotify = false
                            notifyPrefs.serialNotify = false
                            SerialNotify.disable(notifyContext)
                        }
                        needsPermission -> askNotifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else -> {
                            serialNotify = true
                            notifyPrefs.serialNotify = true
                            SerialNotify.enable(notifyContext)
                        }
                    }
                },
            )
        }

        // ---- 1.4.0 的五个可选项：默认全关，升级不动任何人的界面 ----

        OptionSwitch(
            title = "悬浮底栏",
            desc = "底栏变成浮在内容之上的胶囊，选中项带一块会滑过去的底。",
            checked = uiOptions.floatingBottomBar,
            onCheckedChange = { onUiOptionsChange(uiOptions.copy(floatingBottomBar = it)) },
        )

        OptionSwitch(
            title = "通透模式",
            desc = "玻璃不再覆盖底色，只留模糊与描边；文字自动加一圈反色柔光保证可读。",
            checked = uiOptions.ultraTranslucent,
            onCheckedChange = { onUiOptionsChange(uiOptions.copy(ultraTranslucent = it)) },
        )

        OptionSwitch(
            title = "莫奈取色套用在模糊上",
            desc = "用系统动态取色的主色 / 次色 / 第三色给背景与模糊层上色。" +
                "需要开动态取色且系统为 Android 12+，否则这个开关不生效。",
            checked = uiOptions.monetBlur,
            onCheckedChange = { onUiOptionsChange(uiOptions.copy(monetBlur = it)) },
        )

        // 预测性返回：这一项**只在"系统自己没有这一层"的版本上**有意义（Android 13 / 14）。
        //
        //  · Android 15（API 35）起，系统级预测性返回对 targetSdk ≥ 35 的应用默认开启，
        //    是**系统在窗口层面**推整个应用（本应用 targetSdk = 36），应用关不掉。
        //    那时再叠我们这层跟手淡出就是同一件事做两遍 —— JmNavHost 在 35+ 上不生效。
        //  · Android 12 及更早没有预测性返回手势，这一项没有可跟的手。
        //
        // 这两种情况都**置灰并写清原因**：与其让用户"关掉了还看到返回动作"以为开关坏了，
        // 不如如实说明。开关的**存值**保留不动（换回 Android 13 / 14 的设备仍按原样生效）。
        val predictiveBackUsable =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        OptionSwitch(
            title = "预测性返回",
            desc = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
                    "Android 15 起由系统提供（返回手势中系统会推动整个窗口），本项不再生效，已停用。"
                predictiveBackUsable ->
                    // 1.4.3 起跟手阶段只做淡、不做缩放（缩放会把整页内容连同封面一起缩），
                    // 文案也必须跟着改 —— 否则用户会去找那个"缩小"。
                    "返回手势进行中，当前页跟手淡出，松手前就能看出要退出。"
                else -> "需要 Android 13 / 14；本机系统没有预测性返回手势，本项不生效。"
            },
            checked = uiOptions.predictiveBack,
            enabled = predictiveBackUsable,
            onCheckedChange = { onUiOptionsChange(uiOptions.copy(predictiveBack = it)) },
        )

        if (!LiteFeatures.ENABLED) {
        // lite 下动效性格被强制为默认（见 Theme.kt），选择器留着只会让人以为可以调
        Text(
            text = "动效",
            style = MaterialTheme.typography.bodyLarge,
            color = c.text,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        Text(
            text = uiOptions.motionStyle.tagline,
            style = MaterialTheme.typography.labelSmall,
            color = c.textTertiary,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        ) {
            MotionStyle.entries.forEachIndexed { index, style ->
                SegmentedButton(
                    selected = uiOptions.motionStyle == style,
                    onClick = { onUiOptionsChange(uiOptions.copy(motionStyle = style)) },
                    shape = SegmentedButtonDefaults.itemShape(index, MotionStyle.entries.size),
                ) {
                    Text(style.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        }
    }
}

/** 一行「标题 + 说明 + 开关」。可选项都走它，省得每加一个开关就抄一遍 Row。 */
@Composable
private fun OptionSwitch(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    /** 这一项在当前系统上还有没有意义；false 时置灰且点不动（例如 Android 15+ 的预测性返回）。 */
    enabled: Boolean = true,
) {
    // lite 下这些可选项在主题层被**强制关闭**（见 Theme.kt 的退化），
    // 界面上就不该再摆出开关 —— 点了没有任何效果，比没有这个开关更让人困惑。
    if (LiteFeatures.ENABLED) return

    val c = JmTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.md)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) c.text else c.textTertiary,
            )
            Text(desc, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            // **关态必须看得见**（1.8.0 用户反馈）：Material3 默认的关态轨道色在自定义调色板
            // （尤其是 lite 的实心风格）下和卡片底色太接近，用户看不出"这个开关现在是关的"，
            // 只能看到一个小小的滑钮。加一条描边，把"轨道在哪、有多长"画出来。
            colors = SwitchDefaults.colors(
                uncheckedTrackColor = c.surface2,
                uncheckedBorderColor = c.textTertiary,
                uncheckedThumbColor = c.textSecondary,
            ),
        )
    }
}

/**
 * 一张风格预览卡。
 *
 * 做法是**临时把该风格的配色与参数提供给子树**，再让正常的 [GlassSurface] 去画 ——
 * 于是预览与本尊共用同一套绘制代码。这样以后改 GlassSurface，预览不会偷偷变得不准。
 */
@Composable
private fun StyleOption(
    style: ThemeStyle,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = paletteFor(style, isDark)
    val spec = Styles.of(style)
    val c = JmTheme.colors

    CompositionLocalProvider(
        LocalJmPalette provides palette,
        LocalJmSpec provides spec,
    ) {
        GlassSurface(
            modifier = modifier,
            level = GlassLevel.Card,
            shape = jmShape(Radius.lg),
            onClick = onClick,
        ) {
            Column(Modifier.fillMaxWidth().padding(Spacing.sm)) {
                // 迷你场景：这张风格下的「背景 + 一张卡片 + 强调色」
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(jmShape(Radius.sm))
                        .background(palette.backdrop.first()),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(Spacing.xs)
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(jmShape(Radius.md))
                            .background(
                                palette.surface2.copy(
                                    alpha = (palette.surface2.alpha * spec.surface.fillAlphaScale)
                                        .coerceIn(0f, 1f),
                                ),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = Spacing.xs + 6.dp, top = Spacing.xs + 8.dp)
                            .size(12.dp)
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(palette.accent),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = style.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) palette.accent else c.text,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Text("当前", style = MaterialTheme.typography.labelSmall, color = palette.accent)
                    }
                }
            }
        }
    }
}

/**
 * 壁纸。
 *
 * 默认档是「纯渐变」：**不发任何请求**。这一点与博客相反（博客默认 Bing），
 * 因为这里是阅读器 —— 想要背景图的人自己开，开的时候也告诉他这会连出去。
 *
 * 四个来源沿用了博客 `src/config/wallpapers.ts` 的接口地址；博客还有「二次元自选」
 * 那种按图源勾选的模式，需要把几百条图集合打包进来，这里没做，换成了一个自定义地址。
 * 缓慢缩放（Ken Burns）也没做：阅读时背景一直在动，是干扰而不是装饰。
 */
@Composable
private fun WallpaperCard() {
    val c = JmTheme.colors
    val store = LocalWallpaperStore.current
    val wall = LocalWallpaper.current
    val scope = rememberCoroutineScope()

    SettingCard(title = "壁纸") {
        Text("来源", style = MaterialTheme.typography.bodyLarge, color = c.text)

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            WallpaperMode.entries.forEach { mode ->
                val on = mode == wall.mode
                Surface(
                    shape = RoundedCornerShape(Radius.pill),
                    color = if (on) c.accentSoft else c.surfaceSunken,
                    onClick = {
                        store?.setMode(mode)
                        if (mode != WallpaperMode.Off) scope.launch { store?.next(force = true) }
                    },
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (on) c.accent else c.textSecondary,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    )
                }
            }
        }

        Text(
            text = wall.mode.desc,
            style = MaterialTheme.typography.labelSmall,
            color = c.textTertiary,
            modifier = Modifier.padding(top = Spacing.sm),
        )

        if (wall.mode == WallpaperMode.Custom) {
            var draft by remember { mutableStateOf(wall.customUrl) }
            OutlinedTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    store?.setCustomUrl(it)
                },
                singleLine = true,
                placeholder = { Text("https://…/image.jpg", color = c.textTertiary) },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )
            TextButton(onClick = { scope.launch { store?.next(force = true) } }) {
                Text("应用这个地址", color = c.accent)
            }
        }

        if (wall.mode != WallpaperMode.Off && wall.mode != WallpaperMode.Custom) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { scope.launch { store?.next(force = true) } },
                    enabled = wall.loading != true,
                ) {
                    Text(if (wall.loading) "取图中…" else "换一张", color = c.accent)
                }
                IconButton(onClick = { scope.launch { store?.next(force = true) } }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "换一张", tint = c.accent)
                }
                Text(
                    text = when (wall.intervalMinutes) {
                        0 -> "自动更换：关闭"
                        else -> "自动更换：每 ${wall.intervalMinutes} 分钟"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                    modifier = Modifier.weight(1f),
                )
            }

            // 间隔档位与博客一致：关闭 / 5 / 15 / 30 / 60 分钟
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(0, 5, 15, 30, 60).forEachIndexed { index, minutes ->
                    SegmentedButton(
                        selected = wall.intervalMinutes == minutes,
                        onClick = { store?.setInterval(minutes) },
                        shape = SegmentedButtonDefaults.itemShape(index, 5),
                    ) {
                        Text(
                            text = if (minutes == 0) "关" else "$minutes",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        if (wall.mode != WallpaperMode.Off) {
            Text(
                text = "模糊 ${wall.blur}",
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary,
                modifier = Modifier.padding(top = Spacing.md),
            )
            Slider(
                value = wall.blur.toFloat(),
                onValueChange = { store?.setBlur(it.toInt()) },
                valueRange = 0f..24f,
                steps = 23,
            )
            Text(
                text = "压暗 ${"%.2f".format(wall.dim)}　（壁纸越花越需要压暗，玻璃才压得住文字）",
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary,
            )
            Slider(
                value = wall.dim,
                onValueChange = { store?.setDim(it) },
                valueRange = 0f..0.6f,
            )
        }

        wall.credit?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = "图片：$it",
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
        wall.error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = c.error,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        Text(
            text = "壁纸是本应用**唯一**会连出去的第三方请求，且只在你选择图片来源后才会发生；" +
                "选「纯渐变」时一个字节都不会发。已取到的地址会缓存在本地轮换（攒够 4 张就不再请求），" +
                "Bing 的地址每天只重新取一次 —— 不为了一张背景图反复打别人的接口。" +
                "阅读页不画壁纸：伪长图的接缝必须看不见。",
            style = MaterialTheme.typography.labelSmall,
            color = c.textTertiary,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

/**
 * 阅读设置。
 *
 * 阅读页顶栏也能切换形态，而且**两边是同一份状态**：在阅读页里切了，这张卡片立刻跟着变，
 * 那次切换也会成为下次打开的默认值。这一条与官方的「只在本次会话内切换」不同，
 * 是刻意的取舍 —— 手机阅读时用户往往长期偏好某一种形态，切一次就该一直记住。
 *
 * 文案必须与行为一致：这里原先写的是「阅读页顶栏改的是当下、这里决定默认」，
 * 而阅读页的切换其实会写回默认值，读起来像两个互不相干的设置。
 */
@Composable
private fun ReadingCard(
    readerMode: ReaderMode,
    onReaderModeChange: (ReaderMode) -> Unit,
) {
    val c = JmTheme.colors
    SettingCard(title = "阅读") {
        Text("默认浏览形态", style = MaterialTheme.typography.bodyLarge, color = c.text)
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        ) {
            ReaderMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = readerMode == mode,
                    onClick = { onReaderModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, ReaderMode.entries.size),
                ) {
                    Text(
                        text = when (mode) {
                            ReaderMode.Scroll -> "纵向滚动"
                            ReaderMode.Page -> "横向翻页"
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Text(
            text = "纵向滚动适合长条页，横向翻页适合单页构图的作品。阅读页顶栏可临时切换。",
            style = MaterialTheme.typography.labelSmall,
            color = c.textTertiary,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

@Composable
private fun PrivacyCard() {
    val c = JmTheme.colors
    SettingCard(title = "隐私与广告") {
        InfoRow("广告接口调用", "从不调用")
        InfoRow("第三方请求", "仅壁纸（默认关闭）")
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
private fun AboutCard(onOpenAbout: () -> Unit) {
    val c = JmTheme.colors
    val themeStyle = JmTheme.spec.style
    SettingCard(title = "关于") {
        InfoRow("版本", BuildConfig.VERSION_NAME)
        InfoRow("界面风格", themeStyle.label)
        InfoRow("动效基准", "${Motion.FAST} / ${Motion.BASE} / ${Motion.SLOW} ms")
        Text(
            text = "WindowGlass 与 Translucent 的令牌移植自 moyingyilang.github.io 的 global.css" +
                "（三径向 + 一线性的渐变底、发丝描边、上缘高光）；Miuix 与 Material 是另外两套表面工艺。" +
                "四套风格换的不只是配色 —— 圆角尺度、表面是实心还是玻璃、字重、按压手感都跟着换。",
            style = MaterialTheme.typography.labelSmall,
            color = c.textTertiary,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
        // 摘要卡只给结论，细节（变体说明、检查更新、链接、许可）在整页里
        TextButton(onClick = onOpenAbout) { Text("更多信息与检查更新 ›") }
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
    /**
     * 读取失败的原因。
     *
     * 与「读到了、但服务端没给这个字段」必须分开显示：前者是网络问题（重试可能就好），
     * 后者是一条协议事实（服务端确实不下发）。两者都写成「未提供」，用户会以为
     * 服务端没这个字段，于是根本不会想到去重试。
     */
    var failure by remember { mutableStateOf<String?>(null) }
    // 用递增的 key 驱动重读：LaunchedEffect(Unit) 只会跑一次，
    // 若只把状态清空而不换 key，按钮点了不会有任何反应
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        checked = false
        failure = null
        val result = runCatching {
            repo.bootstrap()
            repo.settings()
        }
        settings = result.getOrNull()
        failure = result.exceptionOrNull()?.message
        checked = true
    }

    val serverVersion = settings?.jm3Version?.takeIf { it.isNotBlank() }
    val clientVersion = JmSession.DEFAULT_CLIENT_VERSION
    val aligned = serverVersion == null || serverVersion == clientVersion

    SettingCard(title = "服务端") {
        InfoRow(
            "服务端对应官方版本",
            serverVersion ?: when {
                !checked -> "读取中…"
                failure != null -> "读取失败"
                else -> "未提供"
            },
        )

        if (failure != null) {
            Text(
                text = failure.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = c.error,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
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

/** 从 `2026-10-01` 这类日期串里取出"几号"（1..31）；取不出来返回 null。 */
private fun dayOfMonth(date: String?): Int? = date
    ?.trim()
    ?.takeLast(2)
    ?.trimStart('0')
    ?.takeIf { it.isNotEmpty() }
    ?.toIntOrNull()

/**
 * 每日签到（1.5.4）。
 *
 * 放在**账号卡正下方**：它需要登录、读到的是这个账号的签到状态，和上面的账号信息是一件事。
 * 之前它被错插在「外观」卡内部，所以位置不对。
 *
 * 未登录时**不显示签到界面**（日历与进度都没有意义），只留一句「登录后再签到」并可点击去登录。
 */
@Composable
private fun DailyCard(onLogin: () -> Unit) {
    val c = JmTheme.colors
    // ---- 1.5.4 每日签到 ----
    //
    // 签到需要登录（接口要 user_id），未登录时不发起任何请求，只给一句说明。
    // 活动数据按 uid 拉取：换账号必须重新拉，否则会把上一个账号的签到状态显示出来。
    val dailyContext = LocalContext.current
    val auth = remember(dailyContext) {
        (dailyContext.applicationContext as JmApp).authStore
    }
    val authState by auth.state.collectAsStateWithLifecycle()
    val uid = authState.member?.uid
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    var daily by remember { mutableStateOf<DailyPayload?>(null) }
    var dailyLoading by remember { mutableStateOf(false) }
    var dailyNotice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uid) {
        daily = null
        dailyNotice = null
        if (uid.isNullOrBlank()) return@LaunchedEffect
        dailyLoading = true
        daily = runCatching { repo.daily(uid) }.getOrNull()
        dailyLoading = false
    }

    SettingCard(title = "每日签到") {
        when {
            uid.isNullOrBlank() -> Text(
                // 未登录不摆出签到界面（日历与进度都没有意义），只留一个能点的入口：
                // 说明这个功能存在，以及怎么用上它
                text = "登录后再签到",
                style = MaterialTheme.typography.bodyMedium,
                color = c.accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onLogin)
                    .padding(vertical = Spacing.xs),
            )

            dailyLoading -> Text(
                text = "正在读取签到活动…",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )

            daily == null -> Text(
                // 说明两种可能，而不是直接说"失败"：没有进行中的活动也会走到这里
                text = "没读到签到活动（可能当前没有进行中的活动，或网络不通）。",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )

            else -> {
                val d = daily!!
                val complete = Daily.isComplete(d.record)
                // 今天签没签以服务端为准（日历里今天那格 signed）—— 用户点完签到，
                // 这里必须立刻显示「今天已签到」，而不是还摆着一个可点的按钮
                val signedToday = Daily.isSignedToday(
                    d.record,
                    java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH),
                )
                Text(
                    text = d.eventName ?: "签到活动",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.text,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                Text(
                    text = "已签 ${Daily.signedCount(d.record)} / ${Daily.totalDays(d.record)} 天",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
                // 按周铺格子：外层是周、内层是这一周的七天，与服务端返回的结构一致
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    d.record.forEach { week ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            week.forEach { day ->
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(Radius.sm))
                                        .background(if (day.signed) c.accent else c.surface2)
                                        .semantics {
                                            // 无障碍读到的应是"几号、签没签"，而不是一个空方块
                                            contentDescription = buildString {
                                                dayOfMonth(day.date)?.let { append("${it}号") }
                                                append(if (day.signed) "，已签到" else "，未签到")
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    // 只靠颜色用户看不出哪格是哪天，所以写上"几号"
                                    dayOfMonth(day.date)?.let { n ->
                                        Text(
                                            text = n.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (day.signed) c.textOnAccent else c.textSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.padding(top = Spacing.sm),
                ) {
                    Button(
                        // 已经签完就禁用；没有 daily_id 时也禁用（服务端没给可打卡的活动）
                        enabled = !complete && !signedToday && !d.dailyId.isNullOrBlank(),
                        onClick = {
                            val id = d.dailyId ?: return@Button
                            scope.launch {
                                dailyNotice = runCatching { repo.dailyCheck(uid, id) }
                                    .fold(
                                        onSuccess = { res ->
                                            if (Daily.isAlreadyChecked(res.msg)) {
                                                "今天已经签过了"
                                            } else {
                                                // 打完卡立刻重拉，让格子上的状态与按钮跟着变
                                                daily = runCatching { repo.daily(uid) }.getOrNull()
                                                res.msg?.takeIf { it.isNotBlank() } ?: "签到成功"
                                            }
                                        },
                                        onFailure = { "签到失败，稍后再试" },
                                    )
                            }
                        },
                    ) {
                        Text(
                            when {
                                signedToday -> "今天已签到"
                                complete -> "本期已签完"
                                else -> "签到"
                            },
                        )
                    }

                    DailyHistorySection(repo = repo, uid = uid)

                    dailyNotice?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                    }
                }
            }
        }
    }
}
