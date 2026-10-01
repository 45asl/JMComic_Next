package com.jmcomic_next.lyqs.ui.screens.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.request.crossfade
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.image.ScrambleTransformation
import com.jmcomic_next.lyqs.data.remote.dto.ReadImage
import com.jmcomic_next.lyqs.data.remote.dto.ReadPayload
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.components.ErrorBox
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
 * 阅读页：纵向连续滚动。
 *
 * 选择纵向而不是左右翻页 —— 与官方 Web 端的默认形态一致，长条页漫画更易读。
 * 每页图片在解码后按需做切片还原，见 [ScrambleTransformation]。
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
    val c = JmTheme.colors

    Column(modifier = modifier.fillMaxSize()) {
        GlassTopBar(
            title = state.payload?.name ?: "阅读",
            subtitle = state.payload?.let { "共 ${it.images.size} 页" },
            navigation = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = c.accent,
                    )
                }
            },
        )

        val payload = state.payload
        when {
            state.loading -> LoadingBox()

            state.error != null -> ErrorBox(message = state.error.orEmpty(), onRetry = { vm.load() })

            payload == null || payload.images.isEmpty() -> ErrorBox(
                message = "这一话没有可显示的图片",
                onRetry = { vm.load() },
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                items(payload.images, key = { it.image }) { image ->
                    ReaderPage(
                        image = image,
                        aid = payload.id,
                        scrambleId = payload.scrambleId,
                        repo = repo,
                    )
                }
            }
        }
    }
}

/**
 * 单页。
 *
 * 用 3:4 的占位比例而不是固定高度：绝大多数漫画页接近该比例，
 * 这样图片到位前后的位移最小；真实高度仍由图片自身决定（`ContentScale.FillWidth`）。
 */
@Composable
private fun ReaderPage(
    image: ReadImage,
    aid: Int,
    scrambleId: Int,
    repo: JmRepository,
) {
    val context = LocalPlatformContext.current
    val needed = repo.needsUnscramble(image.image, aid, scrambleId)

    val request = ImageRequest.Builder(context)
        .data(image.image)
        .crossfade(true)
        .apply {
            if (needed) {
                transformations(
                    ScrambleTransformation(aid = aid, page = image.fileNameStem),
                )
            }
        }
        .build()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xs)),
    ) {
        AsyncImage(
            model = request,
            contentDescription = "第 ${image.page} 页",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
