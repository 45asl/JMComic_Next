package com.jmcomic_next.lyqs.ui.screens.reader

import com.jmcomic_next.lyqs.data.prefs.SharedPrefsKeyValueStore
import com.jmcomic_next.lyqs.LiteFeatures
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.image.ScrambleTransformation
import com.jmcomic_next.lyqs.data.prefs.AppPrefs
import com.jmcomic_next.lyqs.data.prefs.ReadProgressStore
import com.jmcomic_next.lyqs.data.prefs.ReaderMode
import com.jmcomic_next.lyqs.data.remote.dto.ReadImage
import com.jmcomic_next.lyqs.data.remote.dto.ReadPayload
import com.jmcomic_next.lyqs.data.remote.dto.SeriesItem
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.components.ErrorBox
import com.jmcomic_next.lyqs.ui.LocalUiOptions
import com.jmcomic_next.lyqs.ui.components.GlassLevel
import com.jmcomic_next.lyqs.ui.components.GlassSurface
import com.jmcomic_next.lyqs.ui.components.ambientBase
import com.jmcomic_next.lyqs.ui.components.GlassTopBar
import com.jmcomic_next.lyqs.ui.components.LoadingBox
import com.jmcomic_next.lyqs.ui.theme.jmShape
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Radius
import com.jmcomic_next.lyqs.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class ReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val payload: ReadPayload? = null,
    /** 当前作品的全部章节，用于上一话/下一话切换。 */
    val series: List<SeriesItem> = emptyList(),
    val currentChapterId: String = "",
    /** 正在切换章节（只重载图片，不重新拉目录）。 */
    val switching: Boolean = false,
    /**
     * 收藏 / 点赞 / 评论数。
     *
     * 这三项都是**从已经拉过的 `album` 里带出来的**（`AlbumDetail` 本来就有
     * `is_favorite` / `liked` / `comment_total`），所以底部栏照官方那样放
     * 收藏与点赞**不需要多一个请求** —— 否则为两个图标再加一次详情请求并不划算。
     */
    val favorited: Boolean = false,
    val liked: Boolean = false,
    val likes: Int = 0,
    val commentTotal: Int = 0,
)

class ReaderViewModel(
    private val repo: JmRepository,
    private val readProgress: ReadProgressStore,
    private val comicId: String,
    initialChapterId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState(currentChapterId = initialChapterId))
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        load()
    }

    /**
     * 首次加载：目录与内容一起拉。
     *
     * 目录来自 `album`（章节内嵌在详情里，`chapter` 接口全项目无人调用）。
     * 没有目录也能读，只是不能切换章节，因此目录失败不阻断内容。
     */
    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.bootstrap() }
            val album = runCatching { repo.album(comicId) }.getOrNull()
            val chapter = runCatching { repo.read(_state.value.currentChapterId) }

            _state.update {
                it.copy(
                    loading = false,
                    series = album?.series.orEmpty(),
                    payload = chapter.getOrNull(),
                    error = chapter.exceptionOrNull()?.message,
                    favorited = album?.isFavorite ?: it.favorited,
                    liked = album?.liked ?: it.liked,
                    likes = album?.likes ?: it.likes,
                    commentTotal = album?.commentTotal ?: it.commentTotal,
                )
            }
            chapter.getOrNull()?.let { recordProgress(it) }
        }
    }

    /**
     * 点赞 / 取消点赞。
     *
     * 先按乐观更新切界面：这是一个一次性的装饰动作，为了它让用户盯着按钮等一次往返
     * 不值得。失败再回滚，并把错误说出来（静默失败会让用户以为点中了）。
     */
    fun toggleLike() {
        val before = _state.value
        val next = !before.liked
        _state.update {
            it.copy(
                liked = next,
                likes = (it.likes + if (next) 1 else -1).coerceAtLeast(0),
            )
        }
        viewModelScope.launch {
            runCatching { repo.like(comicId) }.onFailure {
                _state.update { it.copy(liked = before.liked, likes = before.likes, error = it.error) }
            }
        }
    }

    /** 收藏 / 取消收藏（服务端是「切换」语义，不做本地取反猜测之外的事）。 */
    fun toggleFavorite() {
        val before = _state.value
        val next = !before.favorited
        _state.update { it.copy(favorited = next) }
        viewModelScope.launch {
            runCatching { repo.toggleFavorite(comicId) }.onFailure {
                _state.update { it.copy(favorited = before.favorited) }
            }
        }
    }

    /**
     * 切换到另一话。
     *
     * 只重载图片，目录保持不变 —— 一部长篇动辄几百话，重拉目录是纯浪费。
     * 同时在栈内**替换**当前话而不是导航新页面：否则连读十章会留下十层返回栈，
     * 用户按一次返回只退一话，体验很糟。
     *
     * **当前话只在请求成功后才推进**。先改 id 再发请求，失败时就会出现一种很难察觉的
     * 错位：屏幕上还是上一话的图，而 `currentChapterId`、上一话/下一话按钮与进度记录
     * 都已经指向新的一话 —— 于是再点「下一话」会直接跳过没读成的那一话，
     * 然后把跳过的那一话记成读过的。
     */
    fun openChapter(chapterId: String) {
        if (chapterId.isBlank() || chapterId == _state.value.currentChapterId) return
        _state.update { it.copy(switching = true, error = null) }
        viewModelScope.launch {
            val chapter = runCatching { repo.read(chapterId) }
            val payload = chapter.getOrNull()
            _state.update {
                it.copy(
                    switching = false,
                    currentChapterId = if (payload != null) chapterId else it.currentChapterId,
                    payload = payload ?: it.payload,
                    // 失败时说清楚是「换话失败」，而不是让错误文字悬在那儿不提当前是哪一话
                    error = chapter.exceptionOrNull()
                        ?.let { e -> "切换章节失败：${e.message ?: "未知错误"}" },
                )
            }
            // 进度按实际生效的那一话记录：失败时记的是仍然显示着的那一话
            recordProgress(payload ?: return@launch)
        }
    }

    private fun recordProgress(payload: ReadPayload) {
        // 记录的键用作品 id 而不是章节 id：详情页要回答的是「这个作品读到哪一话」
        readProgress.record(comicId, _state.value.currentChapterId)
    }

    /** 相邻章节。目录里找不到当前话时两边都为空（例如目录还没载入）。 */
    fun neighbour(offset: Int): SeriesItem? {
        val s = _state.value
        val index = s.series.indexOfFirst { it.id == s.currentChapterId }
        if (index < 0) return null
        return s.series.getOrNull(index + offset)
    }

    /** 当前话在目录中的序号（从 1 开始），用于展示「第 N 话」。 */
    fun currentIndex(): Int {
        val s = _state.value
        val index = s.series.indexOfFirst { it.id == s.currentChapterId }
        return if (index < 0) 0 else index + 1
    }
}

/**
 * 阅读页。
 *
 * 两种浏览形态（[ReaderMode]），官方 Web 端都有：
 *  - 纵向连续滚动：长条页漫画更连贯，也是官方默认形态
 *  - 横向逐页翻动：每页适配整屏，适合单页构图的作品
 *
 * 底部在顶栏可见时给出一话切换。选底部而不是顶栏：连读时拇指在屏幕下半区，
 * 翻页按钮放顶栏是够不着的。
 */
@Composable
fun ReaderScreen(
    comicId: String,
    chapterId: String,
    mode: ReaderMode,
    onModeChange: (ReaderMode) -> Unit,
    onBack: () -> Unit,
    /** 打开这一话所属作品的评论区。官方底部栏有这个入口，这里保持一致。 */
    onOpenComments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = LocalRepository.current
    val uiOptions = LocalUiOptions.current
    // LocalContext.current 是 composable 读取，需在 remember 之外取
    val context = LocalContext.current
    val readProgress = remember(context) { ReadProgressStore(SharedPrefsKeyValueStore(context, "jm_read_progress")) }
    val vm: ReaderViewModel = viewModel(
        key = "reader-$chapterId",
        factory = viewModelFactory {
            initializer { ReaderViewModel(repo, readProgress, comicId, chapterId) }
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    // 阅读形态由上层托管（与主题一样）。此前这里自己读了一份偏好并直接写回，
    // 于是「我的 → 阅读形态」显示的是进阅读页之前的值，在阅读页里切了形态之后
    // 那张卡片仍是旧选项，再点一下又会把刚做的切换覆盖掉。
    var barsVisible by remember { mutableStateOf(true) }
    var pickerOpen by remember { mutableStateOf(false) }
    val c = JmTheme.colors

    val prev = vm.neighbour(-1)
    val next = vm.neighbour(1)
    val index = vm.currentIndex()

    Box(modifier = modifier.fillMaxSize()) {
        val payload = state.payload
        when {
            state.loading -> LoadingBox()

            state.error != null && payload == null ->
                ErrorBox(message = state.error.orEmpty(), onRetry = { vm.load() })

            payload == null || payload.images.isEmpty() -> ErrorBox(
                message = "这一话没有可显示的图片",
                onRetry = { vm.load() },
            )

            else -> {
                // 用章节 id 作为 key：换话时整体重建，LazyColumn 的滚动位置与 Pager 的页码
                // 才会归零。否则「跳到第 200 话」会停在第 200 话的中段 —— 因为滚动状态
                // 属于组合，而组合在换话时并没有被替换。
                // 底栏与阅读器之间接页码的持有者。底栏是顶栏/错误条的兄弟节点，
                // 位于 key() 之外，拿不到 LazyListState / PagerState；用一个很小的持有者
                // 把「当前页」与「跳页」两个方向接起来，比把两个状态对象提升到外层安全 ——
                // 后者会牵动「换话要重置滚动位置」的 key 逻辑。
                val pageLink = remember(state.currentChapterId) { ReaderPageLink() }

                key(state.currentChapterId) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // 阅读页必须有**不透明**底：壁纸若从页间/页边透出来，
                            // 伪长图的接缝会重新变得可见，这正是 1.1.5 修掉的问题。
                            //
                            // 用与设置页**同一段绘制**（ambientBase）而不是自己写一个色值：
                            // 这里原来在滚动模式取 `c.backdrop.first()`、翻页模式写死
                            // `Color.Black`，于是换成 Material / Miuix 或开莫奈之后，
                            // 阅读页的背景与设置页对不上——用户反馈的「不同步」主要就是这一处。
                            // ambientBase 只会画实色渐变与光斑，不依赖任何透明度，
                            // 所以接缝依然看不见。
                            .ambientBase()
                            .pointerInput(mode) {
                                detectTapGestures(onTap = { barsVisible = !barsVisible })
                            },
                    ) {
                        val toggleBars = { barsVisible = !barsVisible }
                        when (mode) {
                            ReaderMode.Scroll -> ScrollReader(payload, repo, pageLink)
                            // 翻页模式下图片自己带手势检测（缩放/双击），它会先消费掉按下事件，
                            // 外层这个 detectTapGestures 永远等不到 onTap —— 于是「点一下收起工具栏」
                            // 在这一模式下是死的，收起后再没有任何入口能把它调出来。
                            // 因此把回调传进去，由图片自己的检测器负责。
                            ReaderMode.Page -> PagedReader(payload, repo, barsVisible, toggleBars, pageLink)
                        }
                    }
                }

                // 有内容时的错误提示（例如换话失败）：内容留在屏幕上，但必须说清楚刚才那一步没成
                state.error?.let { message ->
                    AnimatedVisibility(
                        visible = barsVisible,
                        enter = slideInVertically { -it },
                        exit = slideOutVertically { -it },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp),
                    ) {
                        GlassSurface(level = GlassLevel.Flyout, shape = jmShape(Radius.md)) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.labelSmall,
                                color = c.accent,
                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = barsVisible,
                    enter = slideInVertically { -it },
                    exit = slideOutVertically { -it },
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    GlassTopBar(
                        title = payload.name?.takeIf { it.isNotBlank() }
                            ?: if (index > 0) "第 $index 话" else "阅读",
                        subtitle = buildString {
                            append("共 ${payload.images.size} 页")
                            if (state.series.isNotEmpty()) append(" · ${index}/${state.series.size}")
                        },
                        navigation = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    tint = c.accent,
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    onModeChange(
                                        if (mode == ReaderMode.Scroll) ReaderMode.Page else ReaderMode.Scroll
                                    )
                                },
                            ) {
                                Icon(
                                    imageVector = if (mode == ReaderMode.Scroll) {
                                        Icons.Filled.SwapVert
                                    } else {
                                        Icons.Filled.SwapHoriz
                                    },
                                    contentDescription = if (mode == ReaderMode.Scroll) {
                                        "切换到横向翻页"
                                    } else {
                                        "切换到纵向滚动"
                                    },
                                    tint = c.accent,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        },
                    )
                }

                // 底栏不再要求「有目录」才出现：页码与滑块在单话作品里同样有用
                AnimatedVisibility(
                    visible = barsVisible,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    ReaderBottomBar(
                        floating = uiOptions.floatingBottomBar,
                        currentPage = pageLink.current,
                        totalPages = state.payload?.images?.size ?: 0,
                        onJump = { pageLink.jump(it) },
                        hasPrev = prev != null,
                        hasNext = next != null,
                        switching = state.switching,
                        onPrev = { prev?.let { vm.openChapter(it.id) } },
                        onNext = { next?.let { vm.openChapter(it.id) } },
                        onOpenPicker = { pickerOpen = true },
                        onOpenComments = onOpenComments,
                        mode = mode,
                        onToggleMode = { onModeChange(if (mode == ReaderMode.Scroll) ReaderMode.Page else ReaderMode.Scroll) },
                        favorited = state.favorited,
                        liked = state.liked,
                        onToggleFavorite = { vm.toggleFavorite() },
                        onToggleLike = { vm.toggleLike() },
                    )
                }
            }
        }
    }

    if (pickerOpen) {
        ChapterPickerDialog(
            series = state.series,
            currentChapterId = state.currentChapterId,
            onDismiss = { pickerOpen = false },
            onPick = { chapterId ->
                pickerOpen = false
                vm.openChapter(chapterId)
            },
        )
    }
}

/**
 * 阅读页底部栏。
 *
 * 结构照官方（JMComic_SRC 的 `ReadNav.tsx`）：**两行** —— 上行「上一话 · 页码滑块 · 下一话」，
 * 下行一排「图标 + 文字」的动作。官方那条是 `bg-nbk bg-opacity-90` 的近黑实色；
 * 这里不照抄那个色值，而是走 [GlassSurface]（设置页同一套玻璃令牌）——
 * 硬编码色值正是「换成 Material / Miuix 之后阅读页跟应用其余部分对不上」的来源。
 *
 * **悬浮版是单独设计的，不是把贴底那条缩窄**：贴底版占满宽度、五个动作带文字标签；
 * 悬浮版是浮起来的圆角卡片，横向空间被左右留白吃掉之后五个文字标签会挤成一条，
 * 所以动作只留图标，页码滑块单独提为上行。
 */
@Composable
private fun ReaderBottomBar(
    floating: Boolean,
    currentPage: Int,
    totalPages: Int,
    onJump: (Int) -> Unit,
    hasPrev: Boolean,
    hasNext: Boolean,
    switching: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenPicker: () -> Unit,
    onOpenComments: () -> Unit,
    mode: ReaderMode,
    onToggleMode: () -> Unit,
    favorited: Boolean,
    liked: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleLike: () -> Unit,
) {
    val enabled = !switching
    val seek: @Composable () -> Unit = {
        PageSeekRow(
            currentPage = currentPage,
            totalPages = totalPages,
            onJump = onJump,
            hasPrev = hasPrev,
            hasNext = hasNext,
            enabled = enabled,
            onPrev = onPrev,
            onNext = onNext,
        )
    }
    val actions: @Composable (Boolean) -> Unit = { showLabels ->
        ReaderActions(
            showLabels = showLabels,
            mode = mode,
            enabled = enabled,
            onToggleMode = onToggleMode,
            onOpenPicker = onOpenPicker,
            onOpenComments = onOpenComments,
            favorited = favorited,
            liked = liked,
            onToggleFavorite = onToggleFavorite,
            onToggleLike = onToggleLike,
        )
    }

    if (floating) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            GlassSurface(
                // 悬浮版同样用真胶囊 + 较轻的一档表面（Flyout 是最不透明的一档，
                // 那会让它看起来像一块实心板而不是浮起来的玻璃）。
                // 这里比主导航的悬浮栏稍实一点（Raised 而非 Card）：它压在漫画页上，
                // 底下可能是任意明暗的图，图标得保证看得清。
                level = GlassLevel.Raised,
                shape = RoundedCornerShape(percent = 50),
                tinted = true,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = Spacing.xxs),
                ) {
                    seek()
                    actions(false)
                }
            }
        }
    } else {
        GlassSurface(
            level = GlassLevel.Flyout,
            shape = RoundedCornerShape(0.dp),
            tinted = true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                seek()
                actions(true)
            }
        }
    }
}

/** 上行：上一话 · 当前页 · 滑块 · 总页数 · 下一话。 */
@Composable
private fun PageSeekRow(
    currentPage: Int,
    totalPages: Int,
    onJump: (Int) -> Unit,
    hasPrev: Boolean,
    hasNext: Boolean,
    enabled: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val c = JmTheme.colors
    val max = (totalPages - 1).coerceAtLeast(0)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        IconButton(onClick = onPrev, enabled = enabled && hasPrev) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = "上一话",
                tint = if (enabled && hasPrev) c.text else c.textTertiary,
            )
        }
        Text(
            text = "${currentPage + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = c.textSecondary,
        )
        Slider(
            value = currentPage.coerceIn(0, max).toFloat(),
            onValueChange = { onJump(it.roundToInt().coerceIn(0, max)) },
            valueRange = 0f..max.toFloat().coerceAtLeast(1f),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$totalPages",
            style = MaterialTheme.typography.labelSmall,
            color = c.textSecondary,
        )
        IconButton(onClick = onNext, enabled = enabled && hasNext) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "下一话",
                tint = if (enabled && hasNext) c.text else c.textTertiary,
            )
        }
    }
}

/** 下行：官方那五个动作。 */
@Composable
private fun ReaderActions(
    showLabels: Boolean,
    mode: ReaderMode,
    enabled: Boolean,
    onToggleMode: () -> Unit,
    onOpenPicker: () -> Unit,
    onOpenComments: () -> Unit,
    favorited: Boolean,
    liked: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleLike: () -> Unit,
) {
    val c = JmTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReaderAction(
            icon = if (mode == ReaderMode.Scroll) Icons.Filled.SwapVert else Icons.Filled.SwapHoriz,
            label = if (mode == ReaderMode.Scroll) "横向" else "纵向",
            showLabel = showLabels, tint = c.text, enabled = enabled, onClick = onToggleMode,
        )
        ReaderAction(
            icon = Icons.AutoMirrored.Filled.List,
            label = "章节",
            showLabel = showLabels, tint = c.text, enabled = enabled, onClick = onOpenPicker,
        )
        ReaderAction(
            icon = Icons.AutoMirrored.Filled.Comment,
            label = "评论",
            showLabel = showLabels, tint = c.text, enabled = enabled, onClick = onOpenComments,
        )
        ReaderAction(
            icon = if (favorited) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            label = "收藏",
            showLabel = showLabels,
            tint = if (favorited) c.accent else c.text,
            enabled = enabled, onClick = onToggleFavorite,
        )
        ReaderAction(
            icon = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            label = "点赞",
            showLabel = showLabels,
            tint = if (liked) c.accent else c.text,
            enabled = enabled, onClick = onToggleLike,
        )
    }
}

@Composable
private fun ReaderAction(
    icon: ImageVector,
    label: String,
    showLabel: Boolean,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = JmTheme.colors
    val shown = if (enabled) tint else c.textTertiary
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = shown, modifier = Modifier.size(22.dp))
        if (showLabel) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = shown)
        }
    }
}

/** 纵向连续滚动。图片按宽度铺满，高度自适应。 */
@Composable
private fun ScrollReader(
    payload: ReadPayload,
    repo: JmRepository,
    pageLink: ReaderPageLink,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 把当前页上报给底栏；同时把「跳页」的能力交给它。
    // 用 snapshotFlow 而不是在组合里直接赋值：组合期间写状态会引发多余的往返重组。
    val total = payload.images.size
    LaunchedEffect(listState, total) {
        pageLink.total = total
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { pageLink.current = it.coerceIn(0, (total - 1).coerceAtLeast(0)) }
    }
    LaunchedEffect(listState) {
        pageLink.jump = { index ->
            scope.launch { listState.animateScrollToItem(index.coerceIn(0, (total - 1).coerceAtLeast(0))) }
        }
    }

    // 顶部留白：第一页要往下让开状态栏 / 摄像头挖孔 / 灵动岛。
    // 阅读页是全屏沉浸的（没有 Scaffold 的 inset 处理），此前第一页紧贴屏幕顶边，
    // 顶部那一条正好被状态栏与挖孔盖住。
    // **只给列表整体加上边距**，不动页与页之间 —— 伪长图的接缝必须严丝合缝。
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topInset = statusBar + Spacing.sm

    // 预取：把当前页前后的若干页提前发出去。
    // 等用户滚到了再开始下载，那时才开始就等于必然先看到占位；
    // 提前几页请求把结果放进内存缓存，滚到时直接命中。
    PrefetchPages(
        payload = payload,
        repo = repo,
        center = listState.firstVisibleItemIndex,
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topInset, bottom = Spacing.xxl),
        // **页间不能有任何间距**：绝大多数作品是「分页拼成的伪长图」，
        // 相邻两页要严丝合缝地接上。官方 Web 端甚至用 `mt-[-2px]` 让两页互相咬合
        // 来消掉拼接处的亚像素缝（见 JMComic_SRC 的 Read.tsx），我这边原来反而加了 4dp 间距。
        // 实测那 4dp 就是接缝处一道明显的横线。
    ) {
        // key 用「下标 + 地址」而不是单用地址：服务端偶尔会重复或留空 image，
        // 单用地址会撞出重复 key 直接崩掉整个阅读页
        itemsIndexed(payload.images, key = { i, img -> "$i-${img.image}" }) { _, image ->
            ReaderImage(
                image = image,
                aid = payload.id,
                scrambleId = payload.scrambleId,
                repo = repo,
                contentScale = ContentScale.FillWidth,
                // 3:4 是绝大多数页的比例，用它撑出占位高度：否则加载中与失败的页是 0 高，
                // 用户看到的是「两张图之间莫名多出一段空白」，也点不到重试
                placeholderRatio = 0.72f,
                // 不裁剪：圆角会在每页四角切掉内容，拼接处也会多出两条弧线
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 横向逐页翻动。
 *
 * 每页用 `ContentScale.Fit` 适配整屏 —— 翻页模式下横向溢出无法靠滚动看到，
 * 必须保证整页可见，这与连续滚动按宽度铺满的处理不同。
 * Compose 的 Pager 自带相邻页预加载，无需手写预取。
 */
@Composable
private fun PagedReader(
    payload: ReadPayload,
    repo: JmRepository,
    showIndicator: Boolean,
    onTap: () -> Unit,
    pageLink: ReaderPageLink,
) {
    val pagerState = rememberPagerState(pageCount = { payload.images.size })
    val scope = rememberCoroutineScope()

    val total = payload.images.size
    LaunchedEffect(pagerState, total) {
        pageLink.total = total
        snapshotFlow { pagerState.currentPage }
            .collect { pageLink.current = it.coerceIn(0, (total - 1).coerceAtLeast(0)) }
    }
    LaunchedEffect(pagerState) {
        pageLink.jump = { index ->
            scope.launch {
                pagerState.animateScrollToPage(index.coerceIn(0, (total - 1).coerceAtLeast(0)))
            }
        }
    }

    // 放大后必须关掉 Pager 自身的滑动，否则「拖动查看局部」会被解释成翻页。
    // 这是缩放手势与翻页手势唯一真正冲突的地方，用「是否处于放大状态」来仲裁。
    var zoomed by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed,
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            ZoomableReaderImage(
                image = payload.images[pageIndex],
                aid = payload.id,
                scrambleId = payload.scrambleId,
                repo = repo,
                onZoomChanged = { zoomed = it },
                onTap = onTap,
            )
        }

        AnimatedVisibility(
            visible = showIndicator,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
        ) {
            GlassSurface(
                level = GlassLevel.Flyout,
                shape = RoundedCornerShape(Radius.pill),
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${payload.images.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = JmTheme.colors.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
        }
    }
}

/**
 * 可缩放的单页图片。
 *
 * 双指缩放 1x–5x、放大后单指平移、双击在「适应屏幕」与 2.5 倍之间切换。
 *
 * 平移不限制边界：Fit 模式下图片的实际显示尺寸由解码结果决定，
 * 强行钳制反而会在边缘出现「拖不动」的错觉；双击回到适应屏幕是可预期的兜底。
 */
@Composable
private fun ZoomableReaderImage(
    image: ReadImage,
    aid: Int,
    scrambleId: Int,
    repo: JmRepository,
    onZoomChanged: (Boolean) -> Unit,
    onTap: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(MIN_ZOOM) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun applyScale(next: Float) {
        scale = next.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (scale <= MIN_ZOOM) offset = Offset.Zero
        onZoomChanged(scale > MIN_ZOOM)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    applyScale(scale * zoom)
                    if (scale > MIN_ZOOM) offset += pan
                }
            }
            .pointerInput(Unit) {
                // 单击与双击都在这里处理：这一层的检测器会先消费按下事件，
                // 外层的同名检测器拿不到任何东西（见 PagedReader 的说明）
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > MIN_ZOOM) applyScale(MIN_ZOOM) else applyScale(DOUBLE_TAP_ZOOM)
                    },
                )
            },
    ) {
        ReaderImage(
            image = image,
            aid = aid,
            scrambleId = scrambleId,
            repo = repo,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * 单页图片。
 *
 * 反切片按需接入：只有 [JmRepository.needsUnscramble] 判定需要时才挂转换
 * （GIF 与 aid 小于 scramble_id 的老漫画都不切）。
 */
@Composable
private fun ReaderImage(
    image: ReadImage,
    aid: Int,
    scrambleId: Int,
    repo: JmRepository,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    placeholderRatio: Float? = null,
) {
    val context = LocalPlatformContext.current
    // 重试次数。每次自增都会换一个新的 memoryCacheKey，让 Coil 真正重新发一次请求 ——
    // 否则相同的请求实例会被判定为「没有变化」，界面会永远停在失败状态上。
    var attempt by remember(image.image) { mutableIntStateOf(0) }

    val request = remember(image.image, attempt, aid, scrambleId) {
        readerImageRequest(context, image, aid, scrambleId, repo, attempt)
    }
    val painter = rememberAsyncImagePainter(request)
    val state by painter.state.collectAsStateWithLifecycle()

    // 占位高度取「最可信的那个比例」：按 URL 记住的最准，其次本作品最近实测的，
    // 最后才是兜底常数。顺序不能反 —— 常数只是没有信息时的赌注，而**猜错就是一次跳动**。
    val ratio = PageRatioMemory.ratioFor(image.image)
        ?: PageRatioMemory.albumRatio(aid)
        ?: placeholderRatio

    // 图到位后把实测比例记下来。这一页自己已经跳完了，但它能让**后面还没加载的页**
    // 一上来就占对高度 —— 这才是「不再被弹走」的关键。
    LaunchedEffect(state) {
        if (state is AsyncImagePainter.State.Success) {
            val size = painter.intrinsicSize
            if (size.isSpecified && size.width > 0f && size.height > 0f) {
                PageRatioMemory.remember(aid, image.image, size.width / size.height)
            }
        }
    }

    Box(modifier) {
        Image(
            painter = painter,
            contentDescription = "第 ${image.page} 页",
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )

        when (state) {
            // 图片失败此前没有任何提示：滚动模式下是一段说不清的空白，
            // 翻页模式下是一整页黑屏，都无法重试，只能整章重新进
            is AsyncImagePainter.State.Error -> PageFallback(
                text = "这一页加载失败 · 点击重试",
                placeholderRatio = ratio,
                onClick = { attempt++ },
            )

            is AsyncImagePainter.State.Loading -> PageFallback(
                text = null,
                placeholderRatio = ratio,
                onClick = null,
            )

            else -> Unit
        }
    }
}

/** 图片的加载中 / 失败占位。[text] 为空表示加载中。 */
@Composable
private fun PageFallback(text: String?, placeholderRatio: Float?, onClick: (() -> Unit)?) {
    val c = JmTheme.colors
    val size = if (placeholderRatio != null) {
        Modifier.fillMaxWidth().aspectRatio(placeholderRatio)
    } else {
        Modifier.fillMaxSize()
    }
    Box(
        // 只有**失败**才画底色与提示：加载中若也画一块底色，在伪长图里就像平白多出一道灰条。
        // 高度照样占住（见 size），所以图到了不会跳。
        modifier = size.then(
            if (text != null) Modifier.background(c.surfaceSunken) else Modifier
        ).then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = c.accent,
                textAlign = TextAlign.Center,
            )
        } else {
            CircularProgressIndicator(
                color = c.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** 预取窗口：见 [LiteFeatures.prefetchBefore] / [LiteFeatures.prefetchAfter]（lite 下更保守）。 */
private val PREFETCH_BEFORE get() = LiteFeatures.prefetchBefore
private val PREFETCH_AFTER get() = LiteFeatures.prefetchAfter

/**
 * 把当前页前后的一段提前发出去。
 *
 * **为什么必须做**：等用户滚到第 N 页才开始下载，那时才开始就等于必然先看到占位 ——
 * 而占位与真实高度一旦不一致，就会在滚动过程中把用户的位置弹一下（见 [PageRatioMemory]）。
 * 提前几页请求，结果会落进 Coil 的内存缓存，滚到时直接命中，既不白屏也不跳。
 *
 * 窗口不对称是有意的：往下滚是主动作，所以后面取多（8 页）；往回看通常只有一两页。
 * 预取用的是与显示**完全一样**的请求（[readerImageRequest]），否则缓存键不同，
 * 预取存下的东西在真正显示时根本命中不了。
 */
@Composable
private fun PrefetchPages(
    payload: ReadPayload,
    repo: JmRepository,
    center: Int,
) {
    val context = LocalPlatformContext.current
    LaunchedEffect(payload.id, payload.scrambleId, center) {
        if (payload.images.isEmpty()) return@LaunchedEffect
        val loader = SingletonImageLoader.get(context)
        val from = (center - PREFETCH_BEFORE).coerceAtLeast(0)
        val to = (center + PREFETCH_AFTER).coerceAtMost(payload.images.lastIndex)
        for (i in from..to) {
            // 当前页由界面自己请求，不在这里重复发
            if (i == center) continue
            loader.enqueue(
                readerImageRequest(
                    context = context,
                    image = payload.images[i],
                    aid = payload.id,
                    scrambleId = payload.scrambleId,
                    repo = repo,
                ),
            )
        }
    }
}

/**
 * 阅读器与底部栏之间的「页码」接口。
 *
 * 为什么需要它：底部栏是顶栏、错误条的**兄弟节点**，位置在 `key(chapterId)` 之外，
 * 因此拿不到 `LazyListState` / `PagerState`；而底栏的滑块又必须能跳到指定页。
 * 用一个很小的持有者把这两个方向接起来，比把两个状态对象提升到外层安全得多 ——
 * 那会牵动「换话必须重置滚动位置」的 key 逻辑（见 ReaderScreen 里那段注释）。
 *
 * [current] 用 State 是因为滑块要跟着动；[jump] 是普通字段，它变化不需要重组。
 */
internal class ReaderPageLink {
    var current by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
    var jump: (Int) -> Unit = {}
}
