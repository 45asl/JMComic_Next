import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * 本地 release 签名配置。
 *
 * `keystore.properties` 与密钥库都**不入库**（见 .gitignore），
 * 因此这里必须容忍文件不存在 —— 否则别人 clone 之后连 `assembleDebug` 都跑不起来。
 * 缺配置时 release 包不签名，其余构建流程照常。
 */
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.jmcomic_next.lyqs"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.jmcomic_next.lyqs"
        minSdk = 24
        targetSdk = 36
        // 1.1.0-beta.1 = 周刊 / 画师与作品库 / 随机推荐 + 一轮缺陷修复
        // 1.1.0-beta.2 = 追更 / 标签收藏 / 整部下载 / 评论发表与删除
        // 1.1.0       = 上面两版的合集（漫画侧功能与官方客户端对齐）
        // 1.1.1       = 真机验证后修掉的三处（周刊类型标签、标签收藏登录引导、账号入口两行两列）
        // 1.1.2       = 用真实账号验证时抓到的四处（响应形态三处 + 非幂等请求被重发一次）
        // 1.1.3       = 真机验评论时发现的两处：正文带 HTML 未处理、删除后计数不减
        // 1.1.4       = 界面美化一轮（卡片尺寸、等高、留白、标题里的箭头提示）
        // 1.1.5       = 阅读页页间缝隙（伪长图被切开）+ 加载占位不再画底色
        // 1.2.0       = 内容屏蔽（关键词 / 分类 / 标签，过滤收敛在数据层）
        // 1.3.0       = 四套界面风格（WindowGlass / Translucent / Miuix / Material）+ 可选壁纸
        // 1.3.1       = Bing 壁纸按屏幕方向取竖屏裁切（改尺寸段，不是拉伸）
        // 1.3.2       = 修 Miuix 连续圆角：右上/左下两个角被反向扫，被切掉一块
        // 1.3.3       = 修玻璃的灰膜与失效的模糊令牌；新增 FlatBlur；Material 改成 Material You 3
        // 1.4.0       = 五个可选项：悬浮底栏 / 莫奈套用到模糊 / 通透模式 / 预测性返回 / Plasma 动效
        // 1.4.1       = 阅读页专项：并发预取、官方式两行底栏、背景与设置同步、首屏避让、不再被弹
        // 1.4.2       = 动画改为「触发前位置 → 触发后位置」驱动（共享元素）+ HyperOS 节奏；玻璃不投影、壁纸模糊归用户、悬浮胶囊
        // 1.5.0       = 四栏切换按 KernelSU 几何重做（整屏位移 + 连续底栏胶囊）、重复作品不放动画、
        //               预测性返回按 SDK 分治（Android 15+ 交给系统）、许可改为 AGPL-3.0-only
        // 1.5.1       = 列表标签屏蔽：列表接口不下发标签，改为后台逐条读详情取标签，命中即从列表隐藏
        // 1.5.2       = 搜索页提示"有结果被标签屏蔽挡住"并给「允许一次」放行；修提示条被列表锚定顶出可视区的坑
        versionCode = 27
        versionName = "1.7.0"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    /**
     * release 构建不跑 lint。
     *
     * 实测：`lintVitalAnalyzeRelease` 单任务耗时 56 秒，占 release 构建总时长的 **77%**
     * （总 73 秒里它占 56 秒），是这台设备上最大的单项开销。
     *
     * lintVital 是「致命问题检查」，属于质量门禁而不是构建的必要环节。
     * 把它从每次构建里摘出来、改成需要时显式运行（`gradle :app:lint`），
     * 能在不放松要求的前提下把日常迭代时间砍掉大半 ——
     * 每次构建都跑一遍静态分析，收益远低于它占用的时间。
     */
    lint {
        checkReleaseBuilds = false
    }

    /**
     * 版本变体（1.8.0）：`full` 是完整版，`lite` 面向极低性能设备。
     *
     * **用 product flavor 而不是 git 分支**：lite 是同一个应用的裁剪版，不是另一款应用。
     * 开分支会让两者越走越远，最终 lite 拿不到任何新修复 —— 而低端设备上的用户恰恰最需要修复。
     *
     * 开关是**编译期常量**（`BuildConfig.LITE`），所以 `if (!BuildConfig.LITE)` 包住的整段代码
     * 与资源会被 R8 一起删掉，而不是"编译进去但不执行"。
     *
     * 两个变体的 applicationId **不同**（lite 带 .lite 后缀）：这样可以同时安装、互不覆盖。
     */
    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
            buildConfigField("boolean", "LITE", "false")
        }
        create("lite") {
            dimension = "edition"
            buildConfigField("boolean", "LITE", "true")
            // 独立包名：两个版本可以**同时安装**、互不覆盖，用户想两个都留着也行。
            // 代价是数据不共享（lite 与 full 各自的收藏/设置互相独立），这是刻意的：
            // 共用一个包名就不可能让两者并存。
            applicationId = "com.jmcomic_next.lyqs.lite"
            // 应用名走 flavor 专属资源覆盖（app/src/lite/res/values/strings.xml）——
            // 项目的 resValues 构建特性是关闭的，资源覆盖不需要打开它。
        }
    }

    buildTypes {
        release {
            // R8 混淆 + 资源压缩。debug 包未混淆时有 24MB，主要体积来自未被裁剪的
            // Compose 与 material-icons-extended —— 后者更明显，图标是按需保留的
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}


/**
 * Compose 编译器的性能报告与指标。
 *
 * 默认**关闭**（每次构建都写报告是没必要的噪音），要看的时候：
 * `gradle :app:compileReleaseKotlin -PcomposeReports --rerun-tasks`
 *
 * 报告会列出「不可跳过的 composable」与「不稳定的类」—— 这是重组性能问题最直接的证据来源，
 * 比凭感觉猜"哪里慢"可靠。
 */
if (project.hasProperty("composeReports")) {
    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose_reports")
        metricsDestination = layout.buildDirectory.dir("compose_metrics")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.google.material)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // 纯 JVM 单元测试：覆盖协议推导与「宽容解析」这两块最容易悄悄改坏、又不需要设备的地方
    testImplementation(libs.junit)
}
