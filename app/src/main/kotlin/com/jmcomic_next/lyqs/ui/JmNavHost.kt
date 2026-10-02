package com.jmcomic_next.lyqs.ui

import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jmcomic_next.lyqs.data.prefs.ReaderMode
import com.jmcomic_next.lyqs.data.prefs.ThemeMode
import com.jmcomic_next.lyqs.ui.theme.ThemeStyle
import com.jmcomic_next.lyqs.ui.components.GlassLevel
import com.jmcomic_next.lyqs.ui.components.GlassSurface
import com.jmcomic_next.lyqs.ui.screens.auth.AuthScreen
import com.jmcomic_next.lyqs.ui.screens.category.CategoryScreen
import com.jmcomic_next.lyqs.ui.screens.creator.CreatorScreen
import com.jmcomic_next.lyqs.ui.screens.creator.CreatorWorkScreen
import com.jmcomic_next.lyqs.ui.screens.week.WeekScreen
import com.jmcomic_next.lyqs.ui.screens.comments.CommentsScreen
import com.jmcomic_next.lyqs.ui.screens.favorites.AccountListKind
import com.jmcomic_next.lyqs.ui.screens.favorites.AccountListScreen
import com.jmcomic_next.lyqs.ui.screens.detail.DetailScreen
import com.jmcomic_next.lyqs.ui.screens.home.HomeScreen
import com.jmcomic_next.lyqs.ui.screens.more.MoreListScreen
import com.jmcomic_next.lyqs.ui.screens.profile.ProfileScreen
import com.jmcomic_next.lyqs.ui.screens.reader.ReaderScreen
import com.jmcomic_next.lyqs.ui.screens.search.SearchScreen
import com.jmcomic_next.lyqs.ui.screens.settings.BlockSettingsScreen
import com.jmcomic_next.lyqs.ui.screens.tags.TagFavoritesScreen
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Radius
import com.jmcomic_next.lyqs.ui.theme.Spacing

/**
 * 底部主导航。
 *
 * [route] 是导航目标，[pattern] 是该目的地注册的路由模式 ——
 * 两者在搜索页上不同（搜索带可选查询参数），高亮判断必须用 [pattern]。
 */
private enum class MainTab(
    val route: String,
    val pattern: String,
    val label: String,
    val icon: ImageVector,
) {
    Home("home", "home", "首页", Icons.Filled.Whatshot),
    Category("category", "category", "分类", Icons.Filled.Sell),
    Search("search", SEARCH_PATTERN, "搜索", Icons.Filled.Search),
    Profile("profile", "profile", "我的", Icons.Filled.Person),
}

private const val SEARCH_PATTERN = "search?q={q}"
private const val ARG_QUERY = "q"
private const val ARG_REASON = "reason"
private const val ROUTE_DETAIL = "detail/{id}"
private const val ROUTE_READ = "read/{comicId}/{chapterId}"
private const val ROUTE_AUTH = "auth?reason={reason}"
private const val ROUTE_FAVORITES = "favorites"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_COMMENTS = "comments/{aid}"
private const val ROUTE_WEEK = "week"
private const val ROUTE_TRACKING = "tracking"
private const val ROUTE_TAGS = "tags"
private const val ROUTE_CREATOR = "creator"
private const val ROUTE_CREATOR_WORK = "creator/work/{id}"
private const val ROUTE_BLOCK = "block"

/**
 * 「更多」列表：首页某个推荐分区的完整列表，或连载更新表（分区 id 26）。
 *
 * 标题作为参数带上，避免为了显示一个标题再去请求一次 `promote`。
 */
private const val MORE_PATTERN = "more/{id}?title={title}"
private const val ARG_SECTION = "id"
private const val ARG_TITLE = "title"

private fun moreFor(sectionId: String, title: String): String =
    "more/$sectionId?title=${Uri.encode(title)}"

/** 按标签打开搜索页。分类页与详情页的标签都走这里。 */
private fun searchFor(tag: String): String = "search?$ARG_QUERY=${Uri.encode(tag)}"

/** 打开登录页，并带上「为什么需要登录」。 */
private fun authFor(reason: String): String = "auth?$ARG_REASON=${Uri.encode(reason)}"

/** 前进入栈的统一写法：加 `launchSingleTop` 免得连点两下压出两层同样的页面。 */
private fun NavHostController.push(route: String) = navigate(route) { launchSingleTop = true }

/**
 * 应用导航图。
 *
 * 底部栏只出现在四个主 Tab 上；详情与阅读是沉浸式页面，进来就盖满全屏。
 * 由「当前路由是否属于主 Tab」决定底部栏显隐，而不是让每个页面各自处理留白。
 */
@Composable
fun JmNavHost(
    readerMode: ReaderMode,
    onReaderModeChange: (ReaderMode) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    themeStyle: ThemeStyle,
    onThemeStyleChange: (ThemeStyle) -> Unit,
    isDark: Boolean,
    uiOptions: UiOptions,
    onUiOptionsChange: (UiOptions) -> Unit,
) {
    val nav = rememberNavController()
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val showBottomBar = MainTab.entries.any { it.pattern == currentRoute }
    val motion = JmTheme.motion

    /** Tab 切换的统一写法。悬浮与贴底两种底栏共用同一段行为。 */
    val switchTab: (MainTab) -> Unit = { tab ->
        if (currentRoute != tab.pattern) {
            nav.navigate(tab.route) {
                // 单层栈：Tab 间切换不堆积历史
                popUpTo(MainTab.Home.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // 预测性返回：手势进行中把当前页跟手推出一点，松手前就能看到「要退出了」。
    var backProgress by remember { mutableFloatStateOf(0f) }
    val canGoBack = nav.previousBackStackEntry != null

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = JmTheme.colors.text,
        bottomBar = {
            if (showBottomBar) {
                if (uiOptions.floatingBottomBar) {
                    FloatingBottomBar(currentRoute = currentRoute, onSelect = switchTab)
                } else {
                    DockedBottomBar(currentRoute = currentRoute, onSelect = switchTab)
                }
            }
        },
    ) { insets ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 跟手退后：轻微缩小 + 左移 + 变淡，进度由系统手势的 progress 驱动
                    val p = if (uiOptions.predictiveBack) backProgress else 0f
                    scaleX = 1f - 0.06f * p
                    scaleY = 1f - 0.06f * p
                    translationX = -size.width * 0.12f * p
                    alpha = 1f - 0.18f * p
                },
        ) {
            NavHost(
                navController = nav,
                startDestination = MainTab.Home.route,
                modifier = Modifier.fillMaxSize().padding(bottom = insets.calculateBottomPadding()),
                // 页面切换动画。以前一个都没配，用的是导航库的默认淡入 ——
                // 配上偏短的时长就显得「啪」地换掉，这正是「生硬」的来源。
                // 曲线、时长与位移比例都随动效性格走（标准 / Plasma）。
                enterTransition = {
                    fadeIn(tween(motion.base, easing = motion.enter)) +
                        slideInHorizontally(tween(motion.base, easing = motion.enter)) {
                            (it * motion.slide).toInt()
                        }
                },
                exitTransition = {
                    fadeOut(tween(motion.fast, easing = motion.exit)) +
                        slideOutHorizontally(tween(motion.base, easing = motion.exit)) {
                            -(it * motion.slide * 0.5f).toInt()
                        }
                },
                popEnterTransition = {
                    fadeIn(tween(motion.base, easing = motion.enter)) +
                        slideInHorizontally(tween(motion.base, easing = motion.enter)) {
                            -(it * motion.slide * 0.5f).toInt()
                        }
                },
                popExitTransition = {
                    fadeOut(tween(motion.fast, easing = motion.exit)) +
                        slideOutHorizontally(tween(motion.base, easing = motion.exit)) {
                            (it * motion.slide).toInt()
                        }
                },
            ) {
                composable(MainTab.Home.route) {
                HomeScreen(
                    dark = isDark,
                    onToggleTheme = {
                        // 顶栏快捷切换：在浅/深之间直接切，不再回落到「跟随系统」
                        onThemeModeChange(if (isDark) ThemeMode.Light else ThemeMode.Dark)
                    },
                    onOpenComic = { id -> nav.push("detail/$id") },
                    onOpenSection = { section ->
                        nav.push(moreFor(section.id, section.title.orEmpty()))
                    },
                    onOpenWeek = { nav.push(ROUTE_WEEK) },
                )
            }

            composable(MainTab.Category.route) {
                CategoryScreen(
                    onOpenTag = { tag -> nav.push(searchFor(tag)) },
                    onOpenComic = { id -> nav.push("detail/$id") },
                    onOpenCreators = { nav.push(ROUTE_CREATOR) },
                )
            }

            composable(
                route = MORE_PATTERN,
                arguments = listOf(
                    navArgument(ARG_SECTION) { type = NavType.StringType },
                    navArgument(ARG_TITLE) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { backStack ->
                MoreListScreen(
                    sectionId = backStack.arguments?.getString(ARG_SECTION).orEmpty(),
                    title = backStack.arguments?.getString(ARG_TITLE).orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenComic = { id -> nav.push("detail/$id") },
                )
            }

            composable(
                route = SEARCH_PATTERN,
                arguments = listOf(
                    navArgument(ARG_QUERY) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { backStack ->
                SearchScreen(
                    onOpenComic = { id -> nav.push("detail/$id") },
                    initialQuery = backStack.arguments?.getString(ARG_QUERY).orEmpty(),
                )
            }

            composable(MainTab.Profile.route) {
                ProfileScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    dynamicColor = dynamicColor,
                    onDynamicColorChange = onDynamicColorChange,
                    readerMode = readerMode,
                    onReaderModeChange = onReaderModeChange,
                    themeStyle = themeStyle,
                    onThemeStyleChange = onThemeStyleChange,
                    isDark = isDark,
                    uiOptions = uiOptions,
                    onUiOptionsChange = onUiOptionsChange,
                    onLogin = { nav.push(authFor("")) },
                    onLogout = {
                        // 登出要走接口，但本地登出不依赖它成功（见 JmRepository.logout）
                        scope.launch { repo.logout() }
                    },
                    onOpenFavorites = { nav.push(ROUTE_FAVORITES) },
                    onOpenHistory = { nav.push(ROUTE_HISTORY) },
                    onOpenTracking = { nav.push(ROUTE_TRACKING) },
                    onOpenTags = { nav.push(ROUTE_TAGS) },
                    onOpenBlock = { nav.push(ROUTE_BLOCK) },
                )
            }

            composable(
                route = ROUTE_AUTH,
                arguments = listOf(
                    navArgument(ARG_REASON) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { backStack ->
                AuthScreen(
                    onBack = { nav.popBackStack() },
                    // 登录成功后退回来源页（详情或我的），由它们自行刷新
                    onLoggedIn = { nav.popBackStack() },
                    reason = backStack.arguments?.getString(ARG_REASON).orEmpty(),
                )
            }

            composable(ROUTE_FAVORITES) {
                AccountListScreen(
                    kind = AccountListKind.Favorites,
                    onBack = { nav.popBackStack() },
                    onOpenComic = { id -> nav.push("detail/$id") },
                    onLogin = { nav.push(authFor("")) },
                )
            }

            composable(ROUTE_WEEK) {
                WeekScreen(
                    onBack = { nav.popBackStack() },
                    onOpenComic = { id -> nav.push("detail/$id") },
                )
            }

            composable(ROUTE_CREATOR) {
                CreatorScreen(
                    onBack = { nav.popBackStack() },
                    onOpenWork = { id -> nav.push("creator/work/$id") },
                )
            }

            composable(
                route = ROUTE_CREATOR_WORK,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { backStack ->
                CreatorWorkScreen(
                    workId = backStack.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenWork = { id -> nav.push("creator/work/$id") },
                )
            }

            composable(
                route = ROUTE_COMMENTS,
                arguments = listOf(navArgument("aid") { type = NavType.StringType }),
            ) { backStack ->
                CommentsScreen(
                    comicId = backStack.arguments?.getString("aid").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onNeedLogin = { nav.push(authFor("发表评论需要登录")) },
                )
            }

            composable(ROUTE_TRACKING) {
                AccountListScreen(
                    kind = AccountListKind.Tracking,
                    onBack = { nav.popBackStack() },
                    onOpenComic = { id -> nav.push("detail/$id") },
                    onLogin = { nav.push(authFor("追更需要登录")) },
                )
            }

            composable(ROUTE_TAGS) {
                TagFavoritesScreen(
                    onBack = { nav.popBackStack() },
                    onLogin = { nav.push(authFor("标签收藏需要登录")) },
                    onOpenTag = { tag -> nav.push(searchFor(tag)) },
                )
            }

            composable(ROUTE_BLOCK) {
                BlockSettingsScreen(onBack = { nav.popBackStack() })
            }

            composable(ROUTE_HISTORY) {
                AccountListScreen(
                    kind = AccountListKind.History,
                    onBack = { nav.popBackStack() },
                    onOpenComic = { id -> nav.push("detail/$id") },
                    onLogin = { nav.push(authFor("")) },
                )
            }

            composable(
                route = ROUTE_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { backStack ->
                val id = backStack.arguments?.getString("id").orEmpty()
                DetailScreen(
                    comicId = id,
                    onBack = { nav.popBackStack() },
                    onOpenComic = { next -> nav.push("detail/$next") },
                    // 阅读页需要作品 id：它要拿系列目录来做上一话/下一话切换
                    onReadChapter = { chapterId -> nav.push("read/$id/$chapterId") },
                    onOpenTag = { tag -> nav.push(searchFor(tag)) },
                    onNeedLogin = { reason -> nav.push(authFor(reason)) },
                    onOpenComments = { nav.push("comments/$id") },
                )
            }

            composable(
                route = ROUTE_READ,
                arguments = listOf(
                    navArgument("comicId") { type = NavType.StringType },
                    navArgument("chapterId") { type = NavType.StringType },
                ),
            ) { backStack ->
                val comicId = backStack.arguments?.getString("comicId").orEmpty()
                ReaderScreen(
                    comicId = comicId,
                    chapterId = backStack.arguments?.getString("chapterId").orEmpty(),
                    // 形态由上层托管：阅读页里切换会同时更新「我的」页的显示（见 ReaderScreen）
                    mode = readerMode,
                    onModeChange = onReaderModeChange,
                    onBack = { nav.popBackStack() },
                    // 底部栏的「评论」入口：评论区是详情页那个页面，按作品 id 打开
                    onOpenComments = { nav.push("comments/$comicId") },
                )
            }
            }
        }

        // 预测性返回的注册位置很关键：`OnBackPressedDispatcher` 按**后加入优先**派发，
        // 而 NavHost 在组合时也注册了自己的返回回调。所以这一句必须写在 NavHost **之后** ——
        // 写在前面会被 NavHost 盖掉，手势永远轮不到这里（这是最容易踩的一个坑）。
        if (uiOptions.predictiveBack) {
            PredictiveBackHandler(enabled = canGoBack) { progress ->
                try {
                    progress.collect { backEvent -> backProgress = backEvent.progress }
                    // 手势走完 → 真正出栈
                    nav.popBackStack()
                } finally {
                    // 取消（collect 抛 CancellationException）或完成后都要归零，
                    // 否则页面会停在退到一半的状态
                    backProgress = 0f
                }
            }
        }
    }
}

/**
 * 贴底底栏（默认）。
 *
 * 就是原来那一条：占满宽度、贴住屏幕底边，用玻璃表面托着 M3 的 [NavigationBar]。
 */
@Composable
private fun DockedBottomBar(
    currentRoute: String?,
    onSelect: (MainTab) -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        level = GlassLevel.Raised,
        shape = RoundedCornerShape(0.dp),
        tinted = true,
    ) {
        NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
            MainTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = currentRoute == tab.pattern,
                    onClick = { onSelect(tab) },
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                    label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}

/**
 * 悬浮底栏（可选，参考 KernelSU 那种做法）。
 *
 * 与贴底那一版的区别不只是「留了边距」：
 *
 *  1. 胶囊形、四周留白、带浮层投影 —— 它是**浮在内容之上**的一块，不是页面的底边；
 *  2. 选中态是**一块会滑过去的圆角底**（跟着 `animateFloatAsState` 滑，不是跳），
 *     而不是 Material 的胶囊指示器 —— 滑动本身是这个形态最核心的手感。
 *
 * 这里仍然为它保留了 Scaffold 的底部内边距（内容不钻到胶囊下面）：
 * 叠在内容上好看，但会把列表最后一条永久压住，得不偿失。
 */
@Composable
private fun FloatingBottomBar(
    currentRoute: String?,
    onSelect: (MainTab) -> Unit,
) {
    val c = JmTheme.colors
    val motion = JmTheme.motion
    val selectedIndex = MainTab.entries
        .indexOfFirst { it.pattern == currentRoute }
        .coerceAtLeast(0)
    val indicator by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(motion.base, easing = motion.enter),
        label = "bottomBarIndicator",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.xs,
                bottom = Spacing.md,
            ),
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            level = GlassLevel.Flyout,
            shape = RoundedCornerShape(Radius.xl),
            tinted = true,
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp)) {
                val itemWidth = maxWidth / MainTab.entries.size

                // 滑动指示器：先画，于是它在下层
                Box(
                    modifier = Modifier
                        .offset(x = itemWidth * indicator)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .padding(horizontal = Spacing.xs, vertical = Spacing.sm)
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(c.accentSoft),
                )

                Row(Modifier.fillMaxSize()) {
                    MainTab.entries.forEach { tab ->
                        val selected = currentRoute == tab.pattern
                        val tint = if (selected) c.accent else c.textSecondary
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(Radius.lg))
                                .clickable { onSelect(tab) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                            )
                        }
                    }
                }
            }
        }
    }
}
