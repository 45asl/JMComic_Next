package com.jmcomic_next.lyqs.ui

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.jmcomic_next.lyqs.ui.theme.JmTheme

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
private const val ROUTE_CREATOR = "creator"
private const val ROUTE_CREATOR_WORK = "creator/work/{id}"

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
    isDark: Boolean,
) {
    val nav = rememberNavController()
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val showBottomBar = MainTab.entries.any { it.pattern == currentRoute }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = JmTheme.colors.text,
        bottomBar = {
            if (showBottomBar) {
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
                                onClick = {
                                    if (currentRoute != tab.pattern) {
                                        nav.navigate(tab.route) {
                                            // 单层栈：Tab 间切换不堆积历史
                                            popUpTo(MainTab.Home.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = {
                                    Text(tab.label, style = MaterialTheme.typography.labelSmall)
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { insets ->
        NavHost(
            navController = nav,
            startDestination = MainTab.Home.route,
            modifier = Modifier.fillMaxSize().padding(bottom = insets.calculateBottomPadding()),
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
                    onLogin = { nav.push(authFor("")) },
                    onLogout = {
                        // 登出要走接口，但本地登出不依赖它成功（见 JmRepository.logout）
                        scope.launch { repo.logout() }
                    },
                    onOpenFavorites = { nav.push(ROUTE_FAVORITES) },
                    onOpenHistory = { nav.push(ROUTE_HISTORY) },
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
                )
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
                ReaderScreen(
                    comicId = backStack.arguments?.getString("comicId").orEmpty(),
                    chapterId = backStack.arguments?.getString("chapterId").orEmpty(),
                    // 形态由上层托管：阅读页里切换会同时更新「我的」页的显示（见 ReaderScreen）
                    mode = readerMode,
                    onModeChange = onReaderModeChange,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
