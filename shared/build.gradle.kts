// :shared —— 跨平台共用的纯 JVM 数据层（2.0.0 起）
//
// 为什么单独成模块：Android 与桌面端要复用同一份接口与数据模型，
// 而 Android 模块编译需要 SDK；把纯逻辑放这里，桌面端构建就完全不依赖 Android 工具链。
// 包名保持不变（com.jmcomic_next.lyqs.*），所以 :app 里的 import 一行都不用改。
plugins {
    id("org.jetbrains.kotlin.jvm")  // 版本由根构建统一声明
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    // api 而不是 implementation：数据模型会出现在 :app 与桌面的公开签名里
    api(libs.kotlinx.serialization.json)
    // 远端层用 api：这些类型会出现在 :app 与桌面的公开签名里
    api(libs.retrofit)
    api(libs.retrofit.serialization)
    api(libs.okhttp)
    api(libs.okhttp.logging)
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
