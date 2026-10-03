package com.jmcomic_next.lyqs.ui

import com.jmcomic_next.lyqs.ui.screens.about.AboutScreen
import com.jmcomic_next.lyqs.ui.screens.random.RandomListScreen
import com.jmcomic_next.lyqs.ui.screens.notifications.NotificationsScreen
import android.net.Uri
import android.os.Build
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
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
import com.jmcomic_next.lyqs.ui.components.BottomBarItem
import com.jmcomic_next.lyqs.ui.components.FloatingBottomBar
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
import com.jmcomic_next.lyqs.ui.theme.MotionSpec

/**
 * 底部主导航。
 *
 * [route] 是导航目标，[pattern] 是该目的地注册的路由模式 ——
 * 两者在搜索页上不同（搜索带可选查询参数），高亮判断必须用 [pattern]。
 *
 * **枚举的书写顺序就是底栏里从左到右的顺序**，1.4.3 起还兼任一件事：
 * Tab 之间横滑的**方向**由两个 Tab 在这个顺序里的先后决定（见 [tabShiftOf]），
 * 所以这里的顺序不能随手调整。
 */
internal enum class MainTab(
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

/**
 * 详情页路由。
 *
 * 两个查询参数都是**列表侧已知、顺手带过去**的：封面地址与标题。
 * 它们不是给「省一次请求」用的（详情照样要请求 `album`），而是给**共享元素**用的：
 * 动画要的"触发后的位置"必须在第一帧就存在，以前封面等数据回来才有，
 * 于是列表→详情的封面动画只走了前半段。见 `DetailScreen` 的 `DetailHeader`。
 */
private const val ROUTE_DETAIL = "detail/{id}?cover={cover}&title={title}"
private const val ARG_COVER = "cover"

/** `title` 这个查询参数名「更多」列表也在用（见 [MORE_PATTERN]），两边共用一个常量。 */
private const val ARG_TITLE = "title"
private const val ROUTE_READ = "read/{comicId}/{chapterId}"
private const val ROUTE_AUTH = "auth?reason={reason}"
private const val ROUTE_FAVORITES = "favorites"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_COMMENTS = "comments/{aid}"
private const val ROUTE_WEEK = "week"
private const val ROUTE_TRACKING = "tracking"
private const val ROUTE_NOTIFICATIONS = "notifications"
private const val ROUTE_RANDOM = "random"
private const val ROUTE_ABOUT = "about"
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

private fun moreFor(sectionId: String, title: String): String =
    "more/$sectionId?title=${Uri.encode(title)}"

/** 按标签打开搜索页。分类页与详情页的标签都走这里。 */
private fun searchFor(tag: String): String = "search?$ARG_QUERY=${Uri.encode(tag)}"

/** 打开登录页，并带上「为什么需要登录」。 */
private fun authFor(reason: String): String = "auth?$ARG_REASON=${Uri.encode(reason)}"

/**
 * 打开详情页时从列表侧带过去的东西。
 *
 * 字段都能缺省：少数入口只拿得到一个 id（搜索页的「按编号精确检索」直接给 aid），
 * 那时封面退回按 id 拼模板、标题等接口返回 —— 但列表入口都拿得到封面和标题，
 * 它们是**共享元素动画的目标矩形**，不能等接口。
 */
data class ComicTarget(
    val id: String,
    val coverUrl: String = "",
    val title: String = "",
)

/**
 * 详情页路由的拼接。
 *
 * 封面是完整 http URL（含 `?` `&` `=`），标题可能是中文、空格、斜杠 ——
 * 两者都是路由串的一部分，**必须转义**，否则 NavHost 会把 URL 自带的
 * 查询串当成自己的参数切碎，`cover` 只剩前半段、`title` 丢掉。
 * 转义用 [Uri.encode]（与 [moreFor] / [searchFor] 同一套做法）。
 */
private fun detailFor(target: ComicTarget): String =
    "detail/${Uri.encode(target.id)}" +
        "?$ARG_COVER=${Uri.encode(target.coverUrl)}" +
        "&$ARG_TITLE=${Uri.encode(target.title)}"

/** 前进入栈的统一写法：加 `launchSingleTop` 免得连点两下压出两层同样的页面。 */
private fun NavHostController.push(route: String) = navigate(route) { launchSingleTop = true }

/**
 * Tab 之间切换：**整屏横滑**（1.5.0 按 KernelSU 的 HorizontalPager 几何重做）。
 *
 * 三处与旧版（0.3 屏宽 + 不透明淡入淡出）不同，正是"看着奇怪"的三个来源：
 *
 *  1. **一屏宽**。0.3 屏宽时两个页面在屏幕中间**叠**着：旧页只退到 -30%、还占着左半边，
 *     新页从 +30% 压上来，两页有一整条 40% 宽的重叠带，其中的内容互相盖 —— 看起来
 *     就是"两页糊在一起抖了一下"。一屏宽时旧页是 [`[0,100%] → [-100%,0]`]、新页是
 *     [`[100%,200%] → [0,100%]`]，任何一帧两页都**正好拼成一整条、不重叠**，
 *     这就是 pager 的几何（也正因为这样才不需要淡）。
 *  2. **不淡**。pager 翻页不动透明度。旧版虽然也没显式淡出，但两页重叠时
 *     后画的那一页天然把前一页"盖"成半可见，观感与淡出无异 —— 换成不重叠之后，
 *     位移本身就是全部信息，不必也不能再淡。
 *  3. **更长**（见 [tabSlideDurationMs]）。位移从 0.3 屏变成 1 屏，走的距离是三倍多；
 *     还用原来的时长就会像"闪一下"，看不出是一页推走了一页。
 *
 * 另外进入与退出**共用同一条时间线**（同一个 spec、同一时长、同一曲线）：
 * 两页是一起动的，谁快谁慢都会让中间那条缝忽宽忽窄。
 *
 * Tab 横滑的时长：取 [MotionSpec.base] 与 [MotionSpec.slow] 的中点。
 *
 * 不写常数：动效性格（标准 / Plasma / HyperOS）本身就是"快慢"档位，
 * 切栏这种整屏位移应该跟着它一起变慢变快。
 */

internal fun tabSlideDurationMs(motion: MotionSpec): Int = (motion.base + motion.slow) / 2

/**
 * 进入页的起始位移：往右边的 Tab 切（shift = 1）就从右边一屏外进来。
 *
 * 抽成函数是为了能被单测钉住 —— "整屏"这个数字正是动画看起来对不对的关键。
 */
internal fun tabSlideEnterOffset(shift: Int, width: Int): Int = width * shift

/** 退出页的目标位移：与进入方向相反，整屏退出去，两页始终拼成一条。 */
internal fun tabSlideExitOffset(shift: Int, width: Int): Int = -width * shift

/**
 * Tab 切换的方向：1 = 往右边的 Tab 切（目标在底栏里更靠后），-1 = 往左切，
 * 0 = **这不是一次 Tab 切换**。
 *
 * 判定用的是两个目的地在 [MainTab] 里的**下标差**（这里只取符号，位移比例固定），
 * 不是写死的方向 —— 调整底栏顺序时动画会自己跟着改。
 *
 * 条件是"两边都是主 Tab"：搜索 Tab 还有别的入口（详情页的标签会 push 到
 * `search?q=…`），那时初始态是详情而不是 Tab，不能当成横滑，否则一次正常的
 * "进详情"会平白横着飘一下。返回 0 时调用方退回淡入淡出。
 */
internal fun tabShiftOf(initialRoute: String?, targetRoute: String?): Int {
    val from = tabIndexOf(initialRoute)
    val to = tabIndexOf(targetRoute)
    if (from < 0 || to < 0 || from == to) return 0
    return if (to > from) 1 else -1
}

/**
 * 目的地属于底栏第几个 Tab；不是主 Tab 返回 -1。
 *
 * 比的是路由模式里 `?` 之前的那一段：搜索页带不带查询参数都是同一个 Tab
 * （`destination.route` 给的是注册的模式 `search?q={q}`，这里容错两种写法）。
 */
private fun tabIndexOf(route: String?): Int {
    val head = route?.substringBefore('?') ?: return -1
    return MainTab.entries.indexOfFirst { it.pattern.substringBefore('?') == head }
}

/**
 * **此刻正在做横滑的那些 Tab 路由** —— 也就是"同一帧里同时可见两个以上主 Tab"的那几个。
 *
 * 传进来的是 `navController.visibleEntries` 的 `destination.route`（当前目的地 +
 * 正在转场的目的地）。返回空集表示"没有横滑"，此时所有页面照常参与共享元素。
 *
 * 为什么这件事必须由路由算、而不能由页面自己判断：共享元素的匹配只看 key。
 * 横滑时出页与入页同时挂在组合树上，同一部作品的两张卡会挂着同一个 key，
 * 于是 Compose 会把出页那张当成"触发前的位置"、入页那张当成"触发后的位置"，
 * 播一次"从出页的卡飞进入页的卡"（真机日志实测：同一个 `jm-cover-*` 在两个
 * 不同的 visibility scope 上同时 `isMatchFound=true`）。用户点的是底栏，
 * 两个位置之间没有"这张卡变成了那张卡"的因果，所以横滑中的两页都摘掉共享键。
 *
 * 判据与页面横滑动画的那条完全一致（见 [tabShiftOf]）：**两个都是主 Tab 才算横滑**。
 * 一个主 Tab + 一个别的目的地（详情 / 更多 / 周刊 / 收藏…）不是横滑 ——
 * 那正是"列表 → 详情"和"详情 → 列表"，共享元素必须照常工作。
 */
internal fun slidingTabRoutesOf(routes: List<String?>): Set<String> {
    // 归一成 Tab 的路由模式：这样"搜索页带不带查询串"只会算成一个 Tab，
    // 调用方也能直接拿 `MainTab.pattern` 去比对。
    val tabs = routes.mapNotNull { route ->
        val index = tabIndexOf(route)
        if (index >= 0) MainTab.entries[index].pattern else null
    }.distinct()
    // 两个以上才算横滑：一个主 Tab + 一个别的目的地不是横滑。
    return if (tabs.size > 1) tabs.toSet() else emptySet()
}

/**
 * 进入态。
 *
 * Tab 之间切换时**从目标 Tab 所在的那一侧滑入**（往右切就从右边进），
 * 其余情况仍是淡入 —— 整屏页面之间没有位移可言（见 [jmSharedElement] 那段规则）。
 */
private fun enterFor(
    initialRoute: String?,
    targetRoute: String?,
    motion: MotionSpec,
): EnterTransition {
    val shift = tabShiftOf(initialRoute, targetRoute)
    if (shift == 0) return fadeIn(tween(motion.base, easing = motion.enter))
    return slideInHorizontally(tabSlideSpec(motion)) { width ->
        tabSlideEnterOffset(shift, width)
    }
}

/** 退出态：Tab 之间切换时朝相反的一侧滑出，其余情况淡出。 */
private fun exitFor(
    initialRoute: String?,
    targetRoute: String?,
    motion: MotionSpec,
): ExitTransition {
    val shift = tabShiftOf(initialRoute, targetRoute)
    if (shift == 0) return fadeOut(tween(motion.base, easing = motion.exit))
    return slideOutHorizontally(tabSlideSpec(motion)) { width ->
        tabSlideExitOffset(shift, width)
    }
}

/**
 * Tab 横滑的动画规格 —— 进入与退出**用同一个**。
 *
 * 弹性风格（Miuix）用 spring：起手快、收尾带一点点过冲，像被推到位；
 * 其余风格用 tween，时长见 [tabSlideDurationMs]，曲线取该风格的"进入"曲线。
 *
 * 不在这里区分 enter/exit 曲线：整屏滑动是一次**成对**的位移（一页进、一页出），
 * 两条曲线不同就会看到两页之间的接缝一宽一窄。要变就一起变。
 */
private fun tabSlideSpec(motion: MotionSpec): FiniteAnimationSpec<IntOffset> =
    if (motion.springy) {
        spring(dampingRatio = 0.9f, stiffness = 320f)
    } else {
        tween(durationMillis = tabSlideDurationMs(motion), easing = motion.enter)
    }

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

    // 底栏只认识「第几栏」：这里把 MainTab 翻成它要的形态，路由的判断都留在导航层。
    val barItems = remember { MainTab.entries.map { BottomBarItem(label = it.label, icon = it.icon) } }
    val selectedTabIndex = MainTab.entries
        .indexOfFirst { it.pattern == currentRoute }
        .coerceAtLeast(0)

    // 正在做 Tab 横滑的两页（判定规则与理由都写在 [slidingTabRoutesOf] 上）。
    //
    // 用 `visibleEntries` 而不是 `currentBackStackEntry`：**它就是 NavHost 拿来决定
    // "此刻要组合哪几页" 的那一份状态**（NavHost 内部同样订阅 visibleEntries，
    // 出页要等自己的退出转场跑完才被移出这个列表），所以"两个主 Tab 同时可见"
    // 与"两页同时挂在组合树上"是同一件事、同一帧 —— 拿它当开关不会与内容错开一帧。
    // 反过来 `currentBackStackEntry` 是另一条流，理论上可能与内容列表差一帧，
    // 而这一帧正好就是"两边都挂着同 key、匹配被配上"的那一帧。
    //
    // 语义确认（2.10.2 源码 + 真机日志）：visibleEntries = 当前目的地 + 正在转场的
    // 目的地，**不是整个返回栈**。所以 [home]（已停在首页）或 [home, category]（已停在
    // 分类、首页在栈里）都只有一个 Tab 可见；只有横滑那一瞬 [home, category] 才两个都在。
    val visibleEntries by nav.visibleEntries.collectAsState()
    val slidingTabRoutes = slidingTabRoutesOf(visibleEntries.map { it.destination.route })

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

    // 预测性返回：手势进行中给当前页一点跟手反馈，松手前就能看出「要退出了」。
    //
    // 反馈**只有透明度，没有缩放**（1.4.3）：页面缩小到中心会让整页内容一起"缩"，而退出时
    // 该动的东西只有封面（见 DetailScreen 的共享元素）—— 内容直接消失、封面沿自己的矩形
    // 飞回去。所以跟手阶段也只做淡，不缩。
    //
    // 用 Animatable 而不是普通状态：手势过程中 snapTo 精确跟手（不加插值、不滞后），
    // 手势取消时再 animateTo 回 1.0 —— 否则松手会"啪"地跳回不透明（反馈里的"取消后跳一下"）。
    val backFeedback = remember { Animatable(0f) }
    val canGoBack = nav.previousBackStackEntry != null

    // **我们自己这层"跟手淡出"要不要画** —— 只在"系统没有这一层"的系统版本上画。
    //
    // Android 15（API 35）起，系统级预测性返回动画（返回桌面 / 跨 Activity / 跨任务）对
    // targetSdk ≥ 35 的应用**默认开启**：返回手势进行中，是**系统在窗口层面**把整个应用
    // 推开（本应用 targetSdk = 36，清单里也显式写着 enableOnBackInvokedCallback="true"）。
    // 那一层应用关不掉，也不该关 —— 我们再叠一层整页 alpha，就是同一件事做两遍。
    // 所以 35+ 上这一项直接不生效（设置页里也如实写出来，见 ProfileScreen）。
    //
    // Android 13 / 14（API 33–34）系统默认没有，才轮到我们这层作为可选增强；
    // 更早的系统连 BackEvent 都没有，没有"跟手"可言，注册它只会白白抢走 NavHost 的返回。
    val ownBackFeedback =
        uiOptions.predictiveBack &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM

    // 这一项从"开"变成"关"（用户拨开关、或系统版本不吃这一套）时把跟手值归零。
    // 手势进行到一半把开关关掉的话，收尾那段 animateTo 会随着 handler 一起被取消，
    // backFeedback 会停在中间值上 —— 不归零就是一个永远 < 1 的 alpha 残影。
    LaunchedEffect(ownBackFeedback) {
        if (!ownBackFeedback) backFeedback.snapTo(0f)
    }

    // 底栏实际占多大高度：**量**而不是写常数 —— 贴底/悬浮两套高度不同，
    // 悬浮那套还要加上让开系统手势条的 padding。
    var barInsetPx by remember { mutableIntStateOf(0) }
    val barInset = with(LocalDensity.current) { barInsetPx.toDp() }

    // 共享元素（"触发前位置 → 触发后位置"）必须活在同一个 SharedTransitionLayout 里
    SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = JmTheme.colors.text,
        bottomBar = {
            if (showBottomBar) {
                Box(Modifier.onSizeChanged { barInsetPx = it.height }) {
                    if (uiOptions.floatingBottomBar) {
                        FloatingBottomBar(
                            items = barItems,
                            selectedIndex = selectedTabIndex,
                            onSelect = { index -> switchTab(MainTab.entries[index]) },
                        )
                    } else {
                        DockedBottomBar(currentRoute = currentRoute, onSelect = switchTab)
                    }
                }
            }
        },
    ) { insets ->
        CompositionLocalProvider(
            LocalBottomBarInset provides if (showBottomBar) barInset else 0.dp,
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 跟手后退：**只淡，不缩**，而且**只在系统没有这一层的版本上**才画
                    // （见 ownBackFeedback）。关掉时 alpha 恒为 1 —— 连读都不读
                    // backFeedback，不可能留下任何残影。
                    val p = if (ownBackFeedback) backFeedback.value else 0f
                    alpha = 1f - 0.35f * p
                },
        ) {
            NavHost(
                navController = nav,
                startDestination = MainTab.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    // 有底栏时**不给它留白**：内容从底栏后面穿过去，这才是「悬浮」。
                    // 页面自己按 LocalBottomBarInset 在列表底部留出可滚出的空间。
                    // 没有底栏的页面（详情/阅读等）仍按原来的方式让开系统手势条。
                    .padding(bottom = if (showBottomBar) 0.dp else insets.calculateBottomPadding()),
                // 页面级转场分两类：
                //
                //  1. **Tab 之间**（当前是主 Tab、目标也是主 Tab）：内容按底栏里的先后
                //     位置**连续横滑** —— 切到右边的 Tab 就从右边滑入、旧内容向左滑出，
                //     往左切反之。方向由 [tabShiftOf] 从 `MainTab.entries` 的下标差算出，
                //     不是写死的。位移只有 0.3 屏宽（见 [TAB_SLIDE_FRACTION]）。
                //  2. **其余目的地**（详情/阅读/评论/设置…）：仍然只淡入淡出。
                //     规则是「动画由触发前的位置 → 触发后的位置决定」，而整屏页面
                //     前后都是整屏 —— 位移为零，不该硬编一个方向。真正的位移交给
                //     共享元素（封面），它两处都有位置，见 jmSharedElement。
                //
                // 四段转场共用同一份方向计算：Tab 切换既有 push（点底栏）也有 pop
                // （详情页的标签 push 到搜索页后退回），两边用同一几何，来回才对称。
                enterTransition = {
                    enterFor(initialState.destination.route, targetState.destination.route, motion)
                },
                exitTransition = {
                    exitFor(initialState.destination.route, targetState.destination.route, motion)
                },
                popEnterTransition = {
                    enterFor(initialState.destination.route, targetState.destination.route, motion)
                },
                popExitTransition = {
                    exitFor(initialState.destination.route, targetState.destination.route, motion)
                },
            ) {
                composable(MainTab.Home.route) {
                    CompositionLocalProvider(
                        LocalNavVisibilityScope provides this,
                        // Tab 横滑期间，这一页的封面**退出共享元素**：同一部作品如果
                        // 在出页与入页各有一张卡，两处会挂同一个 key，共享元素就会
                        // 把它当成"出页那张变成了入页那张"来播（见 LocalSharedElementEnabled）。
                        LocalSharedElementEnabled provides (MainTab.Home.pattern !in slidingTabRoutes),
                    ) {
                HomeScreen(
                    dark = isDark,
                    onToggleTheme = {
                        // 顶栏快捷切换：在浅/深之间直接切，不再回落到「跟随系统」
                        onThemeModeChange(if (isDark) ThemeMode.Light else ThemeMode.Dark)
                    },
                    onOpenComic = { target -> nav.push(detailFor(target)) },
                    onOpenSection = { section ->
                        nav.push(moreFor(section.id, section.title.orEmpty()))
                    },
                    onOpenWeek = { nav.push(ROUTE_WEEK) },
                    onOpenRandomList = { nav.push(ROUTE_RANDOM) },
                )
                    }
            }

            composable(MainTab.Category.route) {
                CompositionLocalProvider(
                    LocalNavVisibilityScope provides this,
                    // 同上：横滑中的两页都不参与共享元素。
                    LocalSharedElementEnabled provides (MainTab.Category.pattern !in slidingTabRoutes),
                ) {
                CategoryScreen(
                    onOpenTag = { tag -> nav.push(searchFor(tag)) },
                    onOpenComic = { target -> nav.push(detailFor(target)) },
                    onOpenCreators = { nav.push(ROUTE_CREATOR) },
                )
                }
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
                CompositionLocalProvider(LocalNavVisibilityScope provides this) {
                    MoreListScreen(
                        sectionId = backStack.arguments?.getString(ARG_SECTION).orEmpty(),
                        title = backStack.arguments?.getString(ARG_TITLE).orEmpty(),
                        onBack = { nav.popBackStack() },
                        onOpenComic = { target -> nav.push(detailFor(target)) },
                    )
                }
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
                CompositionLocalProvider(
                    LocalNavVisibilityScope provides this,
                    // 同上：横滑中的两页都不参与共享元素。注意这里比的是**路由模式**
                    // （`destination.route` 给的是 `search?q={q}`），带查询串也还是这一栏。
                    LocalSharedElementEnabled provides (SEARCH_PATTERN !in slidingTabRoutes),
                ) {
                    SearchScreen(
                        onOpenComic = { target -> nav.push(detailFor(target)) },
                        initialQuery = backStack.arguments?.getString(ARG_QUERY).orEmpty(),
                    )
                }
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
                    onOpenNotifications = { nav.push(ROUTE_NOTIFICATIONS) },
                    onOpenAbout = { nav.push(ROUTE_ABOUT) },
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
                    onOpenComic = { target -> nav.push(detailFor(target)) },
                    onLogin = { nav.push(authFor("")) },
                )
            }

            composable(ROUTE_WEEK) {
                CompositionLocalProvider(LocalNavVisibilityScope provides this) {
                WeekScreen(
                    onBack = { nav.popBackStack() },
                    onOpenComic = { target -> nav.push(detailFor(target)) },
                )
                }
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

            composable(ROUTE_RANDOM) {
                RandomListScreen(
                    onBack = { nav.popBackStack() },
                    onOpenComic = { target -> nav.push(detailFor(target)) },
                )
            }

            composable(ROUTE_ABOUT) {
                AboutScreen(onBack = { nav.popBackStack() })
            }

            composable(ROUTE_NOTIFICATIONS) {
                NotificationsScreen(
                    onBack = { nav.popBackStack() },
                    onOpenComic = { id -> nav.push("detail/$id") },
                )
            }

            composable(ROUTE_TRACKING) {
                AccountListScreen(
                    kind = AccountListKind.Tracking,
                    onBack = { nav.popBackStack() },
                    onOpenComic = { target -> nav.push(detailFor(target)) },
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
                    onOpenComic = { target -> nav.push(detailFor(target)) },
                    onLogin = { nav.push(authFor("")) },
                )
            }

            composable(
                route = ROUTE_DETAIL,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    // 两个都有默认值：不带查询串的 `detail/<id>`（外部深链、旧书签）
                    // 仍然能进详情页，只是第一帧没有封面可画
                    navArgument(ARG_COVER) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(ARG_TITLE) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
                // 退出（返回列表）时详情页"除了封面以外的内容直接消失"是最终效果，
                // 但它**不是**靠 `popExitTransition = ExitTransition.None` 做到的：那一版
                // 实测（逐帧像素）更差 —— 目的地不会当场消失，而是"冻"在原样被留了两帧
                // （详情页那几个按钮与退出前逐像素一致、满不透明度），而共享元素的时钟已经
                // 在这两帧里走掉大半，封面第一次被看见时已在 80% 的位置上，回归动画只剩
                // 最后一段。现在的做法见 SharedTransition.kt 的 jmVanishWhenLeaving()：
                // 目的地转场保持原样（共享元素与页面共用同一条时间线），本页内容在共享元素
                // 开始飞的那一帧直接藏掉。
            ) { backStack ->
                val id = backStack.arguments?.getString("id").orEmpty()
                CompositionLocalProvider(LocalNavVisibilityScope provides this) {
                    DetailScreen(
                        comicId = id,
                        // 列表侧带来的封面与标题：详情页拿它当**初始值**，
                        // 于是第一帧就有封面，且落在它最终的位置上（共享元素的目标矩形）
                        initialCoverUrl = backStack.arguments?.getString(ARG_COVER).orEmpty(),
                        initialTitle = backStack.arguments?.getString(ARG_TITLE).orEmpty(),
                        onBack = { nav.popBackStack() },
                        onOpenComic = { next -> nav.push(detailFor(next)) },
                        // 阅读页需要作品 id：它要拿系列目录来做上一话/下一话切换
                        onReadChapter = { chapterId -> nav.push("read/$id/$chapterId") },
                        onOpenTag = { tag -> nav.push(searchFor(tag)) },
                        onNeedLogin = { reason -> nav.push(authFor(reason)) },
                        onOpenComments = { nav.push("comments/$id") },
                    )
                }
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
        }

        }
    }

        // 预测性返回的注册位置很关键：`OnBackPressedDispatcher` 按**后加入优先**派发
        // （androidx 自己的文档也是这么写的：callbacks are invoked in the reverse order
        // in which they are added），而 NavHost 在组合时也注册了自己的返回回调。
        // 所以这一句必须写在 NavHost **之后** —— 写在前面会被 NavHost 盖掉，
        // 手势永远轮不到这里（这是最容易踩的一个坑）。
        //
        // 这里**无条件调用**、用 `enabled` 表达开关，而不是包在 `if` 里 ——
        // androidx 的 PredictiveBackHandler 文档明确要求这样：
        // 「It is important to call this composable unconditionally. Use the enabled
        //   parameter to control whether the handler is active.」
        // 包在 `if` 里会让组合的槽位随开关增删，处理器在派发链里的位置跟着变，
        // 官方说这会带来「recomposition 之后被调用的是另一个处理器」这类不可预期的行为；
        // 而无条件调用时，处理器始终在链上、只是 isBackEnabled=false 被跳过，
        // 返回手势一定落到 NavHost 那一个上。
        PredictiveBackHandler(enabled = ownBackFeedback && canGoBack) { progress ->
            try {
                progress.collect { backEvent -> backFeedback.snapTo(backEvent.progress) }
                // 手势走完 → 真正出栈
                nav.popBackStack()
            } finally {
                // 归零走一段动画，而不是直接赋值：
                //  · 手势取消时，否则页面会"啪"地弹回不透明（反馈里的"取消后跳一下"）；
                //  · 手势完成时，这段同时也是"退出后新页面亮起来"的那一段，曲线连续。
                //
                // 动画必须挂在 rememberCoroutineScope 上 —— 取消时当前这个协程已经
                // 被取消了，在它里面 animateTo 会立刻抛异常，什么都不会发生。
                scope.launch {
                    backFeedback.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(motion.base, easing = motion.exit),
                    )
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
