package com.jmcomic_next.lyqs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jmcomic_next.lyqs.data.prefs.AppPrefs
import com.jmcomic_next.lyqs.data.prefs.ThemeMode
import com.jmcomic_next.lyqs.ui.JmNavHost
import com.jmcomic_next.lyqs.ui.LocalRepository
import com.jmcomic_next.lyqs.ui.components.AmbientBackdrop
import com.jmcomic_next.lyqs.ui.theme.JmTheme

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
        val repository = (application as JmApp).repository

        setContent {
            var themeMode by remember { mutableStateOf(prefs.themeMode) }
            var dynamicColor by remember { mutableStateOf(prefs.dynamicColor) }
            var readerMode by remember { mutableStateOf(prefs.readerMode) }

            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.System -> systemDark
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            CompositionLocalProvider(LocalRepository provides repository) {
                JmTheme(darkTheme = isDark, dynamicColor = dynamicColor) {
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
                            isDark = isDark,
                        )
                    }
                }
            }
        }
    }
}
