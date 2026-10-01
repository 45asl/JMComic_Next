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
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.image.ScrambleTransformation
import com.jmcomic_next.lyqs.data.prefs.AppPrefs
import com.jmcomic_next.lyqs.data.prefs.ReaderMode
import com.jmcomic_next.lyqs.data.remote.dto.ReadImage
import com.jmcomic_next.lyqs.data.remote.dto.ReadPayload
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
)

class ReaderViewModel(
    private val repo: JmRepository,
    private val chapterId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = runCatching {
                repo.bootstrap()
                repo.read(chapterId)
            }
            _state.update {
                it.copy(
                    loading = false,
                    payload = result.getOrNull(),
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }
}

/**
 * 阅读页。
 *
 * 两种浏览形态（[ReaderMode]），都在官方 Web 端存在：
 *  - 纵向连续滚动：长条页漫画更连贯，也是官方默认形态
 *  - 横向逐页翻动：每页适配整屏，适合单页构图的作品
 *
 * 点按屏幕切换沉浸模式（隐藏顶栏与页码条）。阅读时界面元素只在需要时才该存在 ——
 * 全屏浏览状态下它们没有信息价值，反而占掉画面。
 */
@Composable
fun ReaderScreen(
    chapterId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = LocalRepository.current
    val vm: ReaderViewModel = viewModel(
        key = "reader-$chapterId",
        factory = viewModelFactory { initializer { ReaderViewModel(repo, chapterId) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    // LocalContext.current 是 composable 读取，需在 remember 之外取一次
    val context = LocalContext.current
    val prefs = remember(context) { AppPrefs(context) }
    var mode by remember { mutableStateOf(prefs.readerMode) }
    var barsVisible by remember { mutableStateOf(true) }
    val c = JmTheme.colors

    Box(modifier = modifier.fillMaxSize()) {
        val payload = state.payload
        when {
            state.loading -> LoadingBox()

            state.error != null -> ErrorBox(message = state.error.orEmpty(), onRetry = { vm.load() })

            payload == null || payload.images.isEmpty() -> ErrorBox(
                message = "这一话没有可显示的图片",
                onRetry = { vm.load() },
            )

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // 翻页模式用纯黑底：图片是 Fit 适配的，留白处不该透出玻璃底
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
                        title = payload.name ?: "阅读",
                        subtitle = "共 ${payload.images.size} 页",
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
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GlassSurface(
                    level = GlassLevel.Flyout,
                    shape = RoundedCornerShape(Radius.xl),
                    modifier = Modifier.padding(bottom = Spacing.xl),
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${payload.images.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JmTheme.colors.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                            .navigationBarsPadding(),
                    )
                }
            }
        }
    }
}

/**
 * 可缩放的单页图片。
 *
 * 双指缩放、放大后单指平移、双击在「适应屏幕」与 2.5 倍之间切换。
 * 缩放倍率限制在 1x–5x：放到 1x 以下没有阅读意义，只会露出黑边。
 *
 * 平移只在放大状态下生效，并且**不限制边界** —— 限制边界需要知道内容与视口的实际尺寸差，
 * 而在 Fit 模式下图片尺寸由解码结果决定，强行钳制反而会在边缘出现「拖不动」的错觉。
 * 双击即可回到适应屏幕，这是一个可预期的兜底。
 *
 * @param onZoomChanged 通知外层当前是否处于放大状态，用于仲裁翻页手势
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
                        if (scale > MIN_ZOOM) {
                            applyScale(MIN_ZOOM)
                        } else {
                            applyScale(DOUBLE_TAP_ZOOM)
                        }
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
