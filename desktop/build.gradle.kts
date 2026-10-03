plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
}

// 仓库统一在 settings.gradle.kts 里声明（那里设了 FAIL_ON_PROJECT_REPOS）

dependencies {
    implementation(compose.desktop.currentOs)
    // material3 不在 currentOs 里，要单独加（Kotlin/Compose 的实际报错就是第 3 行 unresolved）
    implementation(compose.material3)
}

// 说明：2.0.0 桌面端自成一个 Gradle 构建（不并进 Android 的 settings），
// 这样构建桌面版完全不需要 Android SDK，容器里也就能独立编译。
compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.AppImage,
            )
            packageName = "jmcomic-next"  // deb/rpm 对包名字符有限制，大写与下划线不合法
            packageVersion = "2.0.0"
            description = "JMComic_Next 桌面版"
            vendor = "moyingyilang"
        }
    }
}
