package com.jmcomic_next.lyqs.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
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
import com.jmcomic_next.lyqs.ui.components.GlassLevel
import com.jmcomic_next.lyqs.ui.components.GlassSurface
import com.jmcomic_next.lyqs.ui.components.GlassTopBar
import com.jmcomic_next.lyqs.ui.components.LoadingBox
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.Radius
import com.jmcomic_next.lyqs.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val payload: ReadPayload? = null,
    /** 当前作品的全部章节，用于上一话/下一话切换。 */
    val series: List<SeriesItem> = emptyList(),
    val currentChapterId: String = "",
    /** 正在切换章节（只重载图片，不重新拉目录）。 */
    val switching: Boolean = false,
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
                )
            }
            chapter.getOrNull()?.let { recordProgress(it) }
        }
    }

    /**
     * 切换到另一话。
     *
     * 只重载图片，目录保持不变 —— 一部长篇动辄几百话，重拉目录是纯浪费。
     * 同时在栈内**替换**当前话而不是导航新页面：否则连读十章会留下十层返回栈，
     * 用户按一次返回只退一话，体验很糟。
     */
    fun openChapter(chapterId: String) {
        if (chapterId.isBlank() || chapterId == _state.value.currentChapterId) return
        _state.update { it.copy(currentChapterId = chapterId, switching = true, error = null) }
        viewModelScope.launch {
            val chapter = runCatching { repo.read(chapterId) }
            _state.update {
                it.copy(
                    switching = false,
                    payload = chapter.getOrNull() ?: it.payload,
                    error = chapter.exceptionOrNull()?.message,
                )
            }
            chapter.getOrNull()?.let { recordProgress(it) }
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = LocalRepository.current
    // LocalContext.current 是 composable 读取，需在 remember 之外取
    val context = LocalContext.current
    val readProgress = remember(context) { ReadProgressStore(context) }
    val vm: ReaderViewModel = viewModel(
        key = "reader-$chapterId",
        factory = viewModelFactory {
            initializer { ReaderViewModel(repo, readProgress, comicId, chapterId) }
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    val prefs = remember(context) { AppPrefs(context) }
    var mode by remember { mutableStateOf(prefs.readerMode) }
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (mode == ReaderMode.Page) Color.Black else Color.Transparent)
                        .pointerInput(mode) {
                            detectTapGestures(onTap = { barsVisible = !barsVisible })
                        },
                ) {
                    when (mode) {
                        ReaderMode.Scroll -> ScrollReader(payload, repo)
                        ReaderMode.Page -> PagedReader(payload, repo, barsVisible)
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
                                    mode = if (mode == ReaderMode.Scroll) {
                                        ReaderMode.Page
                                    } else {
                                        ReaderMode.Scroll
                                    }
                                    prefs.readerMode = mode
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

                AnimatedVisibility(
                    visible = barsVisible && state.series.isNotEmpty(),
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    ChapterSwitcher(
                        prevLabel = if (prev != null) "第 ${index - 1} 话" else null,
                        nextLabel = if (next != null) "第 ${index + 1} 话" else null,
                        enabled = !state.switching,
                        onPrev = { prev?.let { vm.openChapter(it.id) } },
                        onNext = { next?.let { vm.openChapter(it.id) } },
                        onOpenPicker = { pickerOpen = true },
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
 * 底部章节切换条。
 *
 * 到头的一侧显示为禁用而不是隐藏 —— 位置固定才能形成肌肉记忆，
 * 忽隐忽现会让用户每次都要找按钮在哪。
 */
@Composable
private fun ChapterSwitcher(
    prevLabel: String?,
    nextLabel: String?,
    enabled: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenPicker: () -> Unit,
) {
    val c = JmTheme.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        GlassSurface(
            level = GlassLevel.Flyout,
            shape = RoundedCornerShape(Radius.xl),
            tinted = true,
            modifier = Modifier.padding(bottom = Spacing.xl),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                IconButton(onClick = onPrev, enabled = enabled && prevLabel != null) {
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = "上一话",
                        tint = if (prevLabel != null) c.accent else c.textTertiary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = prevLabel ?: "已是最前",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (prevLabel != null) c.text else c.textTertiary,
                )
                // 中间是可点入口：长篇只靠左右翻要翻到手酸，必须能直接跳
                TextButton(onClick = onOpenPicker, enabled = enabled) {
                    Text("选择章节", style = MaterialTheme.typography.labelSmall, color = c.accent)
                }
                Text(
                    text = nextLabel ?: "已是最后",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (nextLabel != null) c.text else c.textTertiary,
                )
                IconButton(onClick = onNext, enabled = enabled && nextLabel != null) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "下一话",
                        tint = if (nextLabel != null) c.accent else c.textTertiary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/** 纵向连续滚动。图片按宽度铺满，高度自适应。 */
@Composable
private fun ScrollReader(payload: ReadPayload, repo: JmRepository) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(payload.images, key = { it.image }) { image ->
            ReaderImage(
                image = image,
                aid = payload.id,
                scrambleId = payload.scrambleId,
                repo = repo,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xs)),
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
private fun PagedReader(payload: ReadPayload, repo: JmRepository, showIndicator: Boolean) {
    val pagerState = rememberPagerState(pageCount = { payload.images.size })

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
                detectTapGestures(
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
) {
    val context = LocalPlatformContext.current
    val request = ImageRequest.Builder(context)
        .data(image.image)
        .crossfade(true)
        .apply {
            if (repo.needsUnscramble(image.image, aid, scrambleId)) {
                transformations(ScrambleTransformation(aid = aid, page = image.fileNameStem))
            }
        }
        .build()

    AsyncImage(
        model = request,
        contentDescription = "第 ${image.page} 页",
        contentScale = contentScale,
        modifier = modifier,
    )
}
