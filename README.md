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
| 搜索（关键词、排序、检索字段、年份；历史与热门标签） | 完成 |
| 详情（元信息 / 标签 / 作品 / 演员 / 点赞 / 简介 / 继续阅读 / 章节目录分页 / 相关推荐） | 完成 |
| 阅读（两种浏览形态、上一话/下一话、章节选择器、沉浸模式、双指缩放、切片还原） | 完成 |
| 我的（账号、主题、动态取色、隐私、关于） | 完成 |
| 账号：登录 / 注册 / 忘记密码 / 登出 | 完成 |
| 收藏（详情页收藏按钮、收藏列表、收藏夹切换与移动） | 完成 |
| 收藏夹管理（新建 / 改名 / 删除；收藏后引导归类） | 完成 |
| 观看历史（含单条删除） | 完成 |
| 分类（分类树 + 子分类 + 排序 + 分组标签） | 完成 |

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

## 广告与隐私

**本应用无广告，且这一点是被审计和检查脚本保证的，不是碰巧如此。**

### 官方客户端的广告从哪来

审计 `JMComic3 v2.1.9` 的还原源码后，广告**全部**由客户端**主动拉取**，来源只有两处：

| 接口 | 用途 |
| --- | --- |
| `ad_content_all` | 按 `adKey` 取素材。官方代码里定义了 **60 多个插槽**，覆盖首页（`app_home_top`、`app_home_float`）、详情（`app_detail_introduction_bottom_jm3`）、阅读（`app_chapter_top`、`app_thewayhome`、`app_chapter_last`）、影片、小说、论坛、搜索等 |
| `advertise_all` | 启动时的四封面全屏广告 |

素材的数据形态是 `{adv_id, adv_img, adv_link, adv_name, adv_text, adv_title, adv_recommend}`。

另外官方 Web 端的 `index.html` 里**内联了 Microsoft Clarity 的埋点脚本**（站点 ID 直接写在 HTML 中）。

### 审计结论

- **服务端不会把广告混进业务载荷**。实测 `promote` / `latest` / `search` / `album` / `comic_read`
  五个响应，均搜不到 `adv_` / `ad_content` / `advertise` 任何特征。
- **阅读流的图片全部落在站点自己的 CDN 上**（实测 54 张图同一主机），没有第三方域名夹带。
- 因此「无广告」等价于「不调用那两个接口、不实现任何插槽」——这正是本应用的实现方式。

### 本应用的做法

1. **不实现任何广告插槽，不调用任何广告接口。** 域名黑名单拦不住这两个接口（它们就在 API 主机上），
   所以这一条靠代码层面不引入来保证。
2. **网络层拦截第三方广告与追踪域名**（`AdBlocker`，覆盖广告交易/投放、行为分析、安装归因三类）。
   拦截返回合成的 403 空响应而不是抛异常 —— 抛异常会让「被拦截」与「网络故障」在上层混为一谈。
3. **图片通道共用同一个 OkHttpClient**。Coil 若不显式传入客户端会自建一个默认实例，
   那样拦截器就只覆盖 API 流量，图片通道是敞开的。
4. **不做任何行为采集。**

### 如何验证

```bash
./scripts/check-no-ads.sh      # 源码出现广告接口/插槽/素材字段即失败
```

脚本会先剥掉注释再匹配 —— 因为 `AdBlocker` 的说明里**必须**写明这些接口与字段名，
否则「防的是什么」无从理解。已验证三种情形：仅注释提及 → 通过；
代码中出现 `adKey` → 失败；代码中出现 `adv_img` → 失败。

设置页的「隐私与广告」栏会显示当前屏蔽的域名类数。

## 账号与收藏

### 接口

| 用途 | 方法与路径 | 参数 |
| --- | --- | --- |
| 登录 | POST `login` | `username`, `password` → 响应 `data.jwttoken` + 会员信息 |
| 注册 | POST `register` | `username`, `password`, `password_confirm`, `email`, `gender` |
| 忘记密码 | POST `forgot` | `email` |
| 登出 | POST `logout` | — |
| 收藏列表 | GET `favorite` | `page`, `folder_id`, `o`（默认 `mr`），每页 20 条 |
| **收藏切换** | POST `favorite` | `aid` |
| 收藏夹编辑 | POST `favorite_folder` | `type`(`add`/`edit`/`move`/`del`), `folder_id`, `folder_name`, `aid` |
| 观看历史 | GET `watch_list` | `page` |
| **删除单条历史** | POST `watch_list` | `id` |

### 三个容易搞反的地方

**1. 收藏是一个「切换」接口。** POST `favorite {aid}` 既可收藏也可取消，由响应里的
`type` 告知实际发生了什么（`add` 新收藏 / `remove` 已取消 / `move`·`edit` 移动了分组）。
因此客户端不需要先查「是否已收藏」再决定调哪个接口，界面状态也应以响应为准而不是本地取反 ——
这样即使本地状态早已过时，也会被纠正回真实状态。

**2. `POST watch_list` 是删除历史，不是记录观看。** 这一点极易搞反：官方代码里唯一的调用点是
`ComicList.tsx` 的 `handleDelWatchComic`（菜单项 `del_watch_history`），
整个项目**没有任何地方用它上报观看** —— 观看记录由**服务端在读取章节时自动写入**。
若误把它当成「上报观看」在进入阅读页时调用，表现就是每读一话删掉一条历史。

**3. `like` 的响应是嵌套封套。** 外层 `code == 200` 表示请求成功，而业务结果还在
`data` 里再套一层 `{code, status, msg}`。只看外层会把失败当成功。

### 关于「检查更新」：这里有意不做成更新提示

`setting` 接口会回 `jm3_version`，但它指的是**官方 App** 的版本，与本应用的版本没有可比性 —
拿它提示「有新版本」是误导用户。

本应用把它用作**协议对齐探针**：客户端在 `Tokenparam` 里上报的版本是照官方版本填的
（`JmSession.DEFAULT_CLIENT_VERSION`），「我的 → 服务端」会显示服务端当前对应的官方版本、
本客户端上报的版本，以及两者是否一致。不一致不代表立即不可用，但说明服务端已在面向新的
客户端行为，值得检查。同一卡片也会展示服务端的版本公告正文（`jm3_version_info`）。

### 阅读进度与章节切换

**阅读进度存在本地，不在服务端。** 服务端的观看历史只到作品粒度，不告诉我们上次停在哪一话；
官方客户端同样是本地记录（`Read.tsx` 把 `{comicId: [chapterId...]}` 存进 localStorage）。
本应用存一个「作品 → 上次那一话」的映射（`ReadProgressStore`），详情页据此给出「继续阅读」。

两处判断值得说明：

- **只在记录的那一话仍存在于目录里时才提供「继续阅读」** —— 目录会随作品改版变化，
  指向一个不存在的章节会让用户点一下就报错。
- **读到第一话时不显示** —— 它和「从头开始」没有区别，多一个按钮只是噪音。

**长篇还要能直接跳。** 底部切换条中间的「选择章节」打开章节选择器，
每页 10 话（与详情页的目录同一套切分方式，两处节奏一致），
且**打开时定位到当前话所在的那一页** —— 用户点它多半是想找当前话附近的内容。

**章节切换在栈内替换而不是导航新页面。** 阅读页同时加载系列目录（来自 `album`，
章节内嵌在详情里）与当前话内容；切话只重载图片，目录保持不变 ——
长篇动辄几百话，重拉目录是纯浪费。同时若每话都导航一层，连读十章就会留下十层返回栈，
用户按一次返回只退一话。切换后进度同样落盘。

### 收藏夹

`favorite_folder` 接口用同一个 POST 承担四种动作，靠 `type` 区分：
`add` 新建 / `edit` 改名 / `move` 归类 / `del` 删除。
界面上对应「管理收藏夹」对话框（新建、改名、删除）与收藏列表每行的「移入收藏夹」。

两点交互取舍：

- **收藏后引导归类，但归类是可选的。** 官方在收藏成功后弹出收藏夹选择框，
  这里保持一致，但给了明确的「仅收藏，不归类」出口 —— 归类是整理动作，
  不该变成收藏的必经步骤。另外没有收藏夹时不会弹空对话框。
- **删除收藏夹不等于取消收藏。** 夹内作品会回到「全部」，因此删除确认文案里写明了这一点，
  避免用户以为会连带取消收藏而不敢删。

### 凭证存储

官方客户端把 JWT 明文放在 Web 存储里（`localStorage.setItem("jwttoken", ...)`）。
Android 上应用私有目录本身有沙箱保护，但设备被 root、被取证、或发生备份提取时，
明文 token 是可直接复用的凭据。因此本应用用 **Android Keystore 中的 AES-256 密钥加密后落盘**
（AES/GCM/NoPadding，密钥由系统保管），不保存明文。

解密失败时不抛异常，而是清掉该条目并视为未登录 —— Keystore 密钥可能因系统还原、
锁屏凭据变更而失效，那种情况下正确行为是让用户重新登录。

### 关于过期：有意不照抄官方

官方在本地记了一个 **1 小时**的过期时间（`saveAuthData` 里 `Date.now() + 60 * 60 * 1000`），
到点静默登出。本应用**不硬性执行这个上限** —— 那是客户端对服务端行为的猜测，
token 的真实有效期由服务端决定。正确做法是正常使用，直到服务端明确拒绝（401/403）再登出，
由拦截器统一清会话并提示「登录状态已失效」。

照抄 1 小时硬过期会出现「用着用着突然要重新登录」，而那个 token 可能本来还有效。

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
