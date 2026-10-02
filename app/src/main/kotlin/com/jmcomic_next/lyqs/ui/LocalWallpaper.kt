package com.jmcomic_next.lyqs.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.jmcomic_next.lyqs.data.wallpaper.WallpaperState
import com.jmcomic_next.lyqs.data.wallpaper.WallpaperStore

/**
 * 壁纸状态与操作入口。
 *
 * 与 [LocalRepository] 同样的路子：状态由 Activity 持有并往下提供，
 * 而不是让每个页面各自去读 SharedPreferences —— 换了壁纸要立刻全屏生效，
 * 这必须是一份共享状态。
 *
 * 给默认值而不是抛异常：壁纸是**可选**的装饰，缺了它界面仍然得能画出来
 * （默认就是「纯渐变」这一档，等于没有壁纸）。
 */
val LocalWallpaper = staticCompositionLocalOf { WallpaperState() }

val LocalWallpaperStore = staticCompositionLocalOf<WallpaperStore?> { null }
