package com.jmcomic_next.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.auth.AuthStore
import com.jmcomic_next.lyqs.data.auth.SecureStore
import com.jmcomic_next.lyqs.data.prefs.BlockStore
import com.jmcomic_next.lyqs.data.remote.dto.ListItem
import java.io.File

/**
 * 桌面端第一版界面：首页列表（2.0.0）。
 *
 * 这一版的目的是**把最小闭环跑起来**：引导 → 拉首页 → 显示封面与标题。
 * 详情、阅读、搜索等页面在其后铺开。
 *
 * 数据层与 Android 完全同一份（:shared），这里只提供三个平台实现：
 * 键值存储、密钥来源、界面。
 */
private val repository: JmRepository by lazy {
    val configDir = File(System.getProperty("user.home"), ".config/jmcomic-next")
    val secure = SecureStore(
        prefs = PreferencesKeyValueStore("jm_secure"),
        keys = FileKeyProvider(File(configDir, "keys")),
    )
    JmRepository.create(
        authStore = AuthStore(secure),
        blockStore = BlockStore(PreferencesKeyValueStore("jm_block")),
        debug = false,
    )
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "JMComic_Next",
        state = rememberWindowState(width = 1100.dp, height = 820.dp),
    ) {
        MaterialTheme { HomeScreen() }
    }
}

@Composable
private fun HomeScreen() {
    var items by remember { mutableStateOf<List<ListItem>>(emptyList()) }
    var status by remember { mutableStateOf("正在引导…") }

    LaunchedEffect(Unit) {
        // 无显示环境下唯一能看运行过程的通道就是标准输出：容器里没有窗口管理器，
        // 截图只能证明"窗口在那儿"，证明不了"数据到了没有"。
        runCatching {
            // 引导（主机发现 + 图床主机）必须在任何登录/列表请求之前完成
            repository.bootstrap()
            status = "正在加载首页…"
            val page = repository.latest(1)
            items = page.items
            page.items.take(3).forEach { System.err.println("[界面] 作品：${it.name} · ${it.author}") }
            status = "首页 ${page.items.size} 条" + if (page.hidden > 0) "（屏蔽规则挡掉 ${page.hidden} 条）" else ""
        }.onFailure {
            status = "加载失败：${it.message}"
            System.err.println("[界面] $status")
        }
    }

    LaunchedEffect(status) { System.err.println("[界面] 状态：$status") }

    Column(Modifier.fillMaxSize()) {
        Text(
            text = status,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items, key = { it.id }) { item -> ComicCard(item) }
        }
    }
}

@Composable
private fun ComicCard(item: ListItem) {
    val coverUrl = remember(item.id) { runCatching { repository.coverUrl(item) }.getOrNull() }
    val bitmap = rememberRemoteImage(coverUrl)

    Column(
        modifier = Modifier.clickable { System.err.println("[界面] 点击作品：${item.name} (id=${item.id})") },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Text(
            text = item.name ?: "(无标题)",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = item.author.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
