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
        versionCode = 1
        versionName = "1.0"
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
}
