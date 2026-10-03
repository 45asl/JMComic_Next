package com.jmcomic_next.lyqs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jmcomic_next.lyqs.ui.LocalTagBlocker
import com.jmcomic_next.lyqs.data.prefs.AppPrefs
import com.jmcomic_next.lyqs.data.prefs.ThemeMode
import com.jmcomic_next.lyqs.data.wallpaper.WallpaperMode
import com.jmcomic_next.lyqs.ui.JmNavHost
import com.jmcomic_next.lyqs.ui.UiOptions
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.LocalWallpaper
import com.jmcomic_next.lyqs.ui.LocalWallpaperStore
import com.jmcomic_next.lyqs.ui.components.AmbientBackdrop
import com.jmcomic_next.lyqs.ui.theme.JmTheme
import com.jmcomic_next.lyqs.ui.theme.ThemeStyle
import kotlinx.coroutines.delay

/**
 * 唯一的 Activity。所有界面都是 Compose，导航交给 [JmNavHost]。
 *
 * 主题偏好用 Compose 状态托管（初值来自 SharedPreferences），
 * 而不是每帧去读磁盘 —— 切换时先改状态、再异步落盘。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = AppPrefs(this)
        val app = application as JmApp
        val repository = app.repository
        val wallpaperStore = app.wallpaperStore

        setContent {
            var themeMode by remember { mutableStateOf(prefs.themeMode) }
            var dynamicColor by remember { mutableStateOf(prefs.dynamicColor) }
            var readerMode by remember { mutableStateOf(prefs.readerMode) }
            var themeStyle by remember { mutableStateOf(prefs.themeStyle) }
            var uiOptions by remember { mutableStateOf(prefs.uiOptions) }
            val wallpaper by wallpaperStore.state.collectAsStateWithLifecycle()

            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.System -> systemDark
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            // 第一次需要壁纸时取一张。用 url 是否为空做键：取到之后这个副作用就不再触发，
            // 换图由设置页的「换一张」或下面的定时器负责
            LaunchedEffect(wallpaper.mode, wallpaper.url == null) {
                if (wallpaper.mode != WallpaperMode.Off && wallpaper.url.isNullOrBlank()) {
                    wallpaperStore.next()
                }
            }

            // 自动更换。只在应用活着的时候走（与博客的做法一致：网页关掉计时器也就没了），
            // 不留后台定时任务 —— 为了一张背景图常驻后台不值当
            LaunchedEffect(wallpaper.mode, wallpaper.intervalMinutes) {
                val minutes = wallpaper.intervalMinutes
                if (minutes > 0 && wallpaper.mode != WallpaperMode.Off) {
                    while (true) {
                        delay(minutes * 60_000L)
                        wallpaperStore.next()
                    }
                }
            }

            CompositionLocalProvider(
                LocalRepository provides repository,
                LocalTagBlocker provides app.tagBlocker,
                LocalWallpaperStore provides wallpaperStore,
                LocalWallpaper provides wallpaper,
            ) {
                JmTheme(
                    darkTheme = isDark,
                    dynamicColor = dynamicColor,
                    style = themeStyle,
                    options = uiOptions,
                ) {
                    AmbientBackdrop {
                        JmNavHost(
                            readerMode = readerMode,
                            onReaderModeChange = {
                                readerMode = it
                                prefs.readerMode = it
                            },
                            themeMode = themeMode,
                            onThemeModeChange = {
                                themeMode = it
                                prefs.themeMode = it
                            },
                            dynamicColor = dynamicColor,
                            onDynamicColorChange = {
                                dynamicColor = it
                                prefs.dynamicColor = it
                            },
                            themeStyle = themeStyle,
                            onThemeStyleChange = {
                                themeStyle = it
                                prefs.themeStyle = it
                            },
                            isDark = isDark,
                            uiOptions = uiOptions,
                            onUiOptionsChange = {
                                uiOptions = it
                                prefs.uiOptions = it
                            },
                        )
                    }
                }
            }
        }
    }
}
