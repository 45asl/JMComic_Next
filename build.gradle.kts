// Top-level build file. Plugin versions come from gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // :shared 是纯 Kotlin JVM 模块；在这里 apply false 声明一次，
    // 子模块再用 id 应用即可 —— 否则会出现"插件已在类路径但版本未知"的冲突
    alias(libs.plugins.kotlin.jvm) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
