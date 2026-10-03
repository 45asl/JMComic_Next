package com.jmcomic_next.lyqs.ui.screens.home

import com.jmcomic_next.lyqs.data.Daily
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.ui.ComicTarget
import com.jmcomic_next.lyqs.ui.components.GlassLevel
import com.jmcomic_next.lyqs.ui.components.GlassSurface
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * 首页的「随机本子」浮动按钮（1.5.6）。
 *
 * ## 位置与形态照源码
 *
 * 官方 Web 客户端首页右下角有一列浮动圆按钮（`MainTopBtn.tsx`），其中一个是骰子
 * （`CasinoIcon`），点击直接进 `randomItem[0].id` 的详情页。这里沿用同样的位置与图标。
 *
 * ## 单击 / 长按
 *
 * - **单击**：直接随机跳一本 —— 骰子会**转两圈**再跳。这不是装饰：拉取要一点点时间，
 *   转起来才说明"我在办事"，否则按下到跳转之间会有一小段没有任何反馈的空档。
 * - **长按**：去随机列表页（[com.jmcomic_next.lyqs.ui.screens.random.RandomListScreen]）
 *   挑一批 —— 按用户的要求用**跳转**而不是弹窗。
 *
 * ## 屏蔽
 *
 * 随机来源 `JmRepository.randomRecommend()` **已应用标签屏蔽规则**，被屏蔽的本子抽不到；
 * 一批全被挡掉时列表页会如实说明，不留一片空白。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RandomFab(
    repo: JmRepository,
    bottomInset: Dp,
    onOpenComic: (ComicTarget) -> Unit,
    onOpenRandomList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = JmTheme.colors
    val scope = rememberCoroutineScope()
    // 骰子转动：单击时转两圈。用 Animatable 而不是 animateFloatAsState，
    // 因为要"每次点击都再转一次"，目标值需要累加而不是收敛到某个固定值。
    val spin = remember { Animatable(0f) }

    Box(modifier.padding(end = Spacing.lg, bottom = bottomInset + Spacing.md)) {
        GlassSurface(
            level = GlassLevel.Raised,
            shape = RoundedCornerShape(percent = 50),
            modifier = Modifier
                .size(52.dp)
                .combinedClickable(
                    onClick = {
                        scope.launch {
                            // 先转起来再拉取：把等待时间变成反馈
                            launch { spin.animateTo(spin.value + 720f, tween(600)) }
                            val one = runCatching { repo.bootstrap(); repo.randomRecommend() }
                                .getOrDefault(emptyList()).randomOrNull()
                            if (one != null) {
                                // 封面必须走 repo.coverUrl()：列表里的 image 是相对路径，
                                // 直接当 URL 用会加载不出来（随机页那一版就是这么错的）
                                onOpenComic(
                                    ComicTarget(one.id, repo.coverUrl(one), one.name.orEmpty()),
                                )
                            }
                        }
                    },
                    onLongClick = onOpenRandomList,
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.Casino,
                contentDescription = "随机一本（长按查看一批）；已排除屏蔽名单",
                tint = c.accent,
                modifier = Modifier
                    .padding(14.dp)
                    .rotate(spin.value),
            )
        }
    }
}

/**
 * 首页的**快捷本日签到**按钮（1.5.6）。
 *
 * 位置照源码：官方首页右侧那条浮动按钮列里，日历图标（每日签到）就在骰子的**上面**
 * （`MainTopBtn.tsx` 的 `CalendarTodayIcon`）。区别是它那边是"跳到签到页"，
 * 这里是**点一下直接签**，签完图标变成对钩 —— 用户要的是少点一次。
 *
 * 三个判定都按服务端为准，不靠本地记状态：
 * - 已经签过（服务端日历里今天 `signed`）→ 显示对钩、不发请求；
 * - 没有进行中的活动（`daily_id` 为空）→ 如实说，不当成失败；
 * - 重复打卡由服务端在 `msg` 里说明（见 [com.jmcomic_next.lyqs.data.Daily.isAlreadyChecked]）。
 *
 * **未登录不显示这个按钮**：签到要账号，而"点了让你去登录"在这里是多余的一步。
 */
@Composable
fun DailyQuickFab(
    repo: JmRepository,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val c = JmTheme.colors
    val scope = rememberCoroutineScope()
    val loggedIn = repo.auth.isLoggedIn
    var signedToday by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    // 进首页时问一次服务端"今天签过没有"：本地不记这个状态，换设备/重装都不会错
    LaunchedEffect(loggedIn) {
        if (!loggedIn) {
            signedToday = false
            return@LaunchedEffect
        }
        val uid = repo.auth.member?.uid
        if (uid.isNullOrBlank()) return@LaunchedEffect
        runCatching { repo.daily(uid) }.getOrNull()?.let { d ->
            signedToday = Daily.isSignedToday(
                d.record,
                java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH),
            )
        }
    }

    if (!loggedIn) return

    // 紧贴骰子上方：骰子高 52dp，再留 Spacing.sm 的缝。
    // 注意提示文字**不能**放在这个 Box 里 —— 它会把外壳撑高，按钮就被顶上去了
    //（上一版就是这么高的），所以提示单独摆一层。
    Box(modifier.padding(end = Spacing.lg, bottom = bottomInset + Spacing.md + 52.dp + Spacing.sm)) {
        GlassSurface(
            level = GlassLevel.Raised,
            // 反色：这块用带色的玻璃（tinted），图标取正文色而不是强调色，
            // 这样它和旁边那颗强调色的骰子不会糊成一片
            tinted = true,
            shape = RoundedCornerShape(percent = 50),
            modifier = Modifier.size(52.dp).combinedClickable(onClick = {
                scope.launch {
                    val uid = repo.auth.member?.uid.orEmpty()
                    val d = runCatching { repo.daily(uid) }.getOrNull()
                    val id = d?.dailyId
                    when {
                        id.isNullOrBlank() -> notice = "现在没有进行中的签到活动"
                        d != null && Daily.isSignedToday(
                            d.record,
                            java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH),
                        ) -> {
                            signedToday = true
                            notice = "今天已经签过了"
                        }
                        else -> runCatching { repo.dailyCheck(uid, id) }.fold(
                            onSuccess = { res ->
                                signedToday = true
                                notice = res.msg?.takeIf { it.isNotBlank() } ?: "签到成功"
                            },
                            onFailure = { notice = "签到失败，稍后再试" },
                        )
                    }
                }
            }),
        ) {
            Icon(
                imageVector = if (signedToday) Icons.Filled.Check else Icons.Filled.CalendarMonth,
                contentDescription = if (signedToday) "今天已签到" else "快捷签到",
                tint = c.text,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
    // 提示单独一层：offset 只影响绘制位置，不会把上面那个 Box 撑高
    notice?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = c.textSecondary,
            modifier = modifier
                .padding(end = Spacing.lg, bottom = bottomInset + Spacing.md + 52.dp + Spacing.sm + 56.dp),
        )
    }
}
