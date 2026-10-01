# JMComic_Next

用 Kotlin 重写的 JMComic 客户端。视觉系统移植自 [moyingyilang.github.io](https://moyingyilang.github.io)
的 **Fluent (Windows 11 Acrylic / Mica) × MIUI 毛玻璃** 设计语言。

原文案「Stability and simplicity are paramount」保留为设计原则：**能少不写多，能显式不隐式。**

技术栈：Kotlin · Jetpack Compose · Material 3 · Coroutines/Flow · Retrofit + kotlinx.serialization · Coil 3

---

## 功能

| 页面 | 状态 |
| --- | --- |
| 首页（推荐分区 + 最新上架，滚动续加） | 完成 |
| 搜索（关键词检索） | 完成 |
| 详情（元信息 / 标签 / 简介 / 章节目录分页 / 相关推荐） | 完成 |
| 阅读（纵向连续滚动，图片切片还原） | 完成 |
| 我的（主题、动态取色、关于） | 完成 |
| 分类浏览、热门标签筛选、账号与收藏 | 未实现 |

本应用是**只读浏览客户端**：不做账号体系，因此也没有登录、收藏、观看历史这些依赖服务端账号的功能。

## 目录结构

```
app/src/main/kotlin/com/jmcomic_next/lyqs/
├── JmApp.kt                     应用容器：仓储单例 + Coil 图片加载器
├── MainActivity.kt              唯一 Activity，承载主题状态
├── data/
│   ├── JmRepository.kt          业务数据入口：引导、分页归一、封面地址
│   ├── crypto/JmCrypto.kt       协议加解密（AES-256-ECB）与切片份数推导
│   ├── image/
│   │   ├── JmImage.kt           图片切片还原
│   │   └── ScrambleTransformation.kt   把还原接进 Coil 管线
│   ├── prefs/AppPrefs.kt        主题偏好（SharedPreferences）
│   └── remote/
│       ├── JmSession.kt         会话：时间戳、Token、API/图床主机
│       ├── JmHostDiscovery.kt   主机发现
│       ├── JmRemote.kt          请求 → 解密 → 反序列化
│       ├── JmApi.kt             封套与 Retrofit 接口
│       ├── JmJson.kt            全局 JSON 宽容策略
│       ├── JmPaths.kt           接口路径常量
│       └── dto/                 数据模型 + Flex* 宽容序列化器
└── ui/
    ├── JmNavHost.kt             导航图与底部栏
    ├── LocalRepository.kt       仓储的 CompositionLocal
    ├── theme/                   设计令牌、主题、动效曲线
    ├── components/              玻璃组件、卡片、状态占位
    └── screens/                 home / search / detail / reader / profile
```

## 设计系统

令牌逐项移植自博客的 `src/styles/global.css`，命名规则是「去掉 `--` 前缀后转大驼峰」，
数值原样保留，便于与博客对账。见 `ui/theme/Tokens.kt`。

- **三档表面**：`Card → Raised → Flyout`，不透明度与投影递增
- **玻璃四层叠加**：半透明填充 + MIUI 橙→蓝薄层 + 发丝外描边 + 上缘高光
- **深浅双主题**，默认跟随系统，可手动切换并记忆
- **Material You 动态取色**：可选项而非常态。只替换强调色，玻璃层次仍用本套令牌 ——
  否则整套视觉的识别度会被系统色冲掉

**关于毛玻璃的实现边界**：博客用 `backdrop-filter: blur(40px) saturate(165%)` 采样背后内容，
Compose 无法读取「已绘制内容」再模糊，因此改为「环境渐变底 + 半透明分层面」的等效方案。

**不提供壁纸功能**。博客那套可调壁纸（五种图源）在本应用不适用：阅读场景需要稳定可预期的
背景。毛玻璃只需要一个有色相层次的底就成立，`AmbientBackdrop` 的渐变网格已经足够。

## 协议说明

以下内容来自对官方客户端（`JMComic3 v2.1.9`）的逆向分析，是本项目最不直观也最容易踩坑的部分。

**请求头**
```
Tokenparam: <秒级时间戳>,<客户端版本>
Token:      md5(<时间戳> + "185Hcomic3PAPP7R")
```

**响应解密** —— 关键点：**AES 密钥就是 `Token` 请求头本身**。
以该 md5 十六进制字符串的 UTF-8 字节作为 32 字节密钥，即 AES-256-ECB（PKCS7）。
- 广告接口例外，密钥不带时间戳：`md5("185Hcomic3PAPP7R")`
- 解不开时回退种子 `"18comicAPPContent"` 再试一次
- `data` 字段或为密文（字符串）或为明文（对象），两种都要接受

**时间戳必须固定在会话内**：官方实现的 `time` 是模块级常量，算一次用到底。
若每次请求各算一个时间戳，就会拿新密钥解旧响应，必然乱码。

**主机发现** 用另一个固定种子 `md5("diosfjckwpqpdfjkvnqQjsik")` 解密，得到
`{"Server":[主机...], "jm3_Server":[[主机,线路名]...]}`。
`Server` 是纯字符串数组，`jm3_Server` 才是键值对 —— 两者结构不同。
响应体开头**带 UTF-8 BOM**，`android.util.Base64` 会因此抛异常，必须先过滤字母表。

**图片切片还原** 部分漫画被纵向切条并错位重排。份数由 `md5(aid + page)` 末位推出，
其中 `page` **不是页码，而是图片文件名去掉扩展名的主体**；`aid` 落在不同区间时还会对末位取模。
用错这个参数会让整页图片还原成错序。见 `JmCrypto.sliceCount` 与 `JmImage.unscramble`。

**数据形态**（无公开文档且并不统一，全部按实际情况兼容）
- `promote` → 分区数组：`[{id,title,slug,type,content:[漫画...]}]`，首页是**若干带标题的区块**
- `latest` → 裸数组，或 `{list,total}`（服务端正在演进）
- `search` → `{search_query,total,content}`，数组键是 `content` 而非 `list`，且 `total` 是字符串
- `chapter` 接口**全项目零调用**，章节列表内嵌在 `album` 响应的 `series` 里

## 构建

```bash
gradle :app:assembleDebug     # 产物：app/build/outputs/apk/debug/app-debug.apk
```

要求 JDK 17+（实测 JDK 21）与 AGP 9.4.1（Gradle 9.8+）。

### 在 aarch64 设备（Termux）上构建

官方 Android SDK 的 build-tools 只提供 **linux-x86_64** 原生二进制，在 aarch64 上无法执行。
必须用 arm64 版 build-tools，并通过 `aapt2FromMavenOverride` 指过去，
否则 AGP 会去 Maven 拉 x86_64 的 aapt2 并失败：

```properties
# gradle.properties
android.aapt2FromMavenOverride=<SDK>/build-tools/35.0.1/aapt2
```

其余注意点：
- `local.properties` 里写 `sdk.dir=<SDK 路径>`（该文件不入库）
- `compileSdk` 需要 `compileSdkMinor` 才能表达 37.2 这类带小版本号的平台
- **不要**再应用 `org.jetbrains.kotlin.android` 插件 —— AGP 9 起内置 Kotlin 支持，重复应用会直接报错
- Gradle 发行包由 `services.gradle.org` 重定向到 GitHub，若该网络不通，请使用系统安装的 `gradle`
- 依赖要求 `compileSdk ≥ 37`，`minSdk 24`（新版 androidx 与 Compose 已不支持 21/22）

## 许可

见 [LICENSE](LICENSE)。本项目仅用于学习与个人使用；不提供任何担保。
