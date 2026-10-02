# JMComic_Next

用 Kotlin 与 Jetpack Compose 重写的 JMComic 客户端，视觉系统移植自
[moyingyilang.github.io](https://moyingyilang.github.io) 的
**Fluent（Windows 11 Acrylic / Mica）× MIUI 毛玻璃** 设计语言。

原则沿用了原来的那句话 ——「Stability and simplicity are paramount」：
**能少不写多，能显式不隐式。**

技术栈：Kotlin · Jetpack Compose · Material 3 · Coroutines/Flow · Retrofit + kotlinx.serialization · Coil 3

> 这是一个**只读的阅读客户端**：能浏览、搜索、阅读、登录、收藏、看评论；
> 不做发帖、投票、购买，也不包含官方的任何广告或埋点。详见〈[无广告与隐私](#无广告与隐私)〉。

---

## 功能

| 页面 | 说明 |
| --- | --- |
| 首页 | 推荐分区（横向滑动）+ 最新上架（滚到底自动续加），每个分区可点「更多」看完整列表 |
| 分区「更多」 | 服务端为每个分区准备了完整列表；「连载更新」这一块按星期 + 类型展示每周更新表 |
| 搜索 | 关键词、排序、检索字段、年份/月份筛选；本地搜索历史与热门标签 |
| 分类 | 分类树 + 子分类 + 排序档位（含月榜/周榜）+ 分组标签 |
| 详情 | 元信息、标签、作品、演员、点赞、评论入口、简介、章节目录、相关推荐 |
| 阅读 | 两种形态（纵向连续滚动 / 横向逐页翻动）、上一话下一话、章节选择器、沉浸模式、双指缩放、切片还原 |
| 账号 | 登录 / 注册 / 忘记密码 / 登出；凭证加密落盘 |
| 收藏 | 详情页收藏按钮（服务端是「切换」语义）、收藏列表、收藏夹切换与归类 |
| 收藏夹管理 | 新建 / 改名 / 删除；收藏后可选是否归类 |
| 观看历史 | 列表 + 单条删除 |
| 评论 | 只读，分页 |
| 我的 | 账号状态、主题（浅/深/跟随系统）、动态取色、阅读形态、隐私说明、服务端信息、关于 |

## 安装

从 [Releases](https://github.com/moyingyilang/JMComic_Next/releases) 或自行构建，
产物在 `app/build/outputs/apk/`：

| 变体 | 体积 | 说明 |
| --- | --- | --- |
| `release/app-release.apk` | ≈ 2.9 MB | 正式包，已开启 R8 混淆与资源压缩，需要签名（见〈构建〉） |
| `debug/app-debug.apk` | ≈ 25 MB | 调试包，包名带 `.debug` 后缀，可与正式包同时安装；会打印网络请求日志 |

要求 **Android 7.0（API 24）**及以上。

## 界面与设计

设计令牌逐项移植自博客的 `src/styles/global.css`，命名规则是「去掉 `--` 前缀后转大驼峰」，
数值原样保留，方便与博客对账。见 `ui/theme/Tokens.kt`。

- **三档表面**：`Card → Raised → Flyout`，不透明度与投影递增
- **玻璃四层叠加**：半透明填充 + MIUI 橙→蓝薄层 + 发丝外描边 + 上缘高光
- **深浅双主题**：默认跟随系统，可手动切换并记忆
- **Material You 动态取色**：可选项而非常态。只替换强调色，玻璃层次仍用本套令牌 ——
  否则整套视觉的识别度会被系统色冲掉

**关于毛玻璃的实现边界**：博客用 `backdrop-filter: blur(40px) saturate(165%)` 采样背后内容，
Compose 无法读取「已绘制内容」再模糊，因此改为「环境渐变底 + 半透明分层面」的等效方案。

**不提供壁纸功能。** 博客那套可调壁纸（五种图源）在本应用不适用：阅读场景需要稳定可预期的
背景。毛玻璃只需要一个有色相层次的底就成立，`AmbientBackdrop` 的渐变网格已经足够。

## 项目结构

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
│   ├── auth/                    凭证存储（Android Keystore 加密）与登录态
│   ├── prefs/                   主题、阅读形态、阅读进度
│   └── remote/                  会话、主机发现、请求与解密、广告拦截、DTO
└── ui/
    ├── JmNavHost.kt             导航图与底部栏
    ├── theme/                   设计令牌、主题、动效曲线
    ├── components/              玻璃组件、卡片、状态占位、统一的「加载更多」页脚
    └── screens/                 home / more / search / category / detail / reader /
                                 comments / favorites / auth / profile
```

## 构建

要求 JDK 17+（实测 JDK 21）与 AGP 9.4.1（Gradle 9.8+）。

```bash
gradle :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
gradle :app:assembleRelease    # app/build/outputs/apk/release/app-release.apk
```

**Release 签名**：签名信息放在 `keystore.properties`（不入库，见 `.gitignore`），密钥库本身也在仓库之外：

```properties
storeFile=/path/to/jm-release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

**在 aarch64 设备（如 Termux）上构建**，有两个硬约束：

- 官方 SDK 的 build-tools 只有 **linux-x86_64** 原生二进制，aarch64 上无法执行。必须用
  arm64 版 build-tools，并通过 `aapt2FromMavenOverride` 指过去，否则 AGP 会去 Maven 拉 x86_64 的 aapt2 并失败。
- `local.properties` 里写 `sdk.dir=<SDK 路径>`（该文件不入库）；`compileSdk` 需要 `compileSdkMinor`
  才能表达 37.2 这类带小版本号的平台。

另外两点：**不要再应用 `org.jetbrains.kotlin.android` 插件**（AGP 9 起内置 Kotlin 支持，重复应用会直接报错）；
Gradle 发行包由 `services.gradle.org` 重定向到 GitHub，若该网络不通，请使用系统安装的 `gradle`。

⚠️ `app/build.gradle.kts` 里显式关掉了 AGP 的 `enableResourceOptimizations`，**不要顺手打开**：
它会让 release 包产出**空的资源表**（没有 `AndroidManifest.xml`、没有 `resources.arsc`，
`pm install` 直接失败），而 debug 变体不走这一步，所以问题只在发布包里出现。

更多构建期的实测数据、踩过的坑与验证方法，见 [`docs/engineering-notes.md`](docs/engineering-notes.md)。

## 协议说明

以下内容来自对官方客户端（`JMComic3 v2.1.9`）的逆向分析，是本项目最不直观也最容易踩坑的部分。

**请求头**
```
Tokenparam: <秒级时间戳>,<客户端版本>
Token:      md5(<时间戳> + "185Hcomic3PAPP7R")
```

**响应解密** —— 关键点：**AES 密钥就是 `Token` 请求头本身**。
以该 md5 十六进制字符串的 UTF-8 字节作为 32 字节密钥，即 AES-256-ECB（PKCS7）。
解不开时回退种子 `"18comicAPPContent"` 再试一次；`data` 字段或为密文（字符串）或为明文（对象），两种都要接受。

两处容易写错的地方：

- **时间戳要固定，而且解密必须用「这次请求实际发出去的那个」**。会话时间戳会被换掉
  （失败重试时），并发下拿「响应到达时」的时间戳去解旧响应必然乱码。本应用的做法是拦截器
  取一次时间戳、同时生成两个头，并把它钉在请求上，响应回来时按键解密。
- **失败响应长这样**：`HTTP 401 {"code":401,"data":[],"errorMsg":"无效的用户名和\/或密码!"}`。
  可读文案在 `errorMsg`（不是 `msg`），且 `data` 是**空的明文数组** —— 所以要先判状态码再解密，
  否则每次密码错误都会被当成「解密失败」，还白跑一次请求。JWT 失效同样是 401（`data` 解出来是
  `{"type":"auth_fail"}`），而 **403 不该当作凭证失效**（也可能来自中间设备）。

**主机发现** 用固定种子 `md5("diosfjckwpqpdfjkvnqQjsik")` 解密，得到
`{"Server":[主机...], "jm3_Server":[[主机,线路名]...]}`（`Server` 是纯字符串数组，`jm3_Server` 才是键值对）。
响应体开头**带 UTF-8 BOM**，`android.util.Base64` 会因此抛异常，必须先过滤字母表。
主机是随机挑的，所以出现网络类失败时要把它标记为可疑、下次重新发现 ——
否则挑中一个失效域名后，整个进程内怎么刷新都没用。

**图片切片还原** 部分漫画被纵向切条并错位重排。份数由 `md5(aid + page)` 末位推出，
其中 `page` **不是页码，而是图片文件名去掉扩展名的主体**；`aid` 落在不同区间时还会对末位取模。
用错这个参数会让整页图片还原成错序。见 `JmCrypto.sliceCount` 与 `JmImage.unscramble`。

**数据形态**（没有公开文档，且并不统一，全部按实际情况兼容）

- `promote` → 分区数组：`[{id,title,slug,type,content:[漫画...]}]`，首页是若干带标题的区块
- `latest` → 裸数组，或 `{list,total}`（服务端正在演进）
- `search` → `{search_query,total,content}`，数组键是 `content` 而非 `list`，`total` 是字符串
- `chapter` 接口**全项目零调用**，章节列表内嵌在 `album` 响应的 `series` 里

## 首页的「更多」与连载更新表

首页的推荐区是服务端切好的几十个分区，每个分区**只给十几条**，而分区标题里写着
「→右滑看更多→」。因此每个分区标题右侧都有「更多」，点进去是该分区的完整列表。

两个接口的语义不同，混用会静默出错：

| 用途 | 接口 | 页码 | 总数 | 终点 |
| --- | --- | --- | --- | --- |
| 普通分区更多 | `promote_list?id=&page=` | **0 起算** | `total` 是字符串，每页 30 条 | `ceil(total/30)` 页 |
| 连载更新 | `serialization?type=&date=&page=` | **1 起算** | 没有 total | 末页返回 `{"error":"没有资料"}` |

- **页码一个从 0 起、一个从 1 起。** 传错不会报错，而是**整段跳过第一页**。
- **末页形态也不同。** 普通分区回空数组且 `total` 不变，能按总数推算；连载更新连 `total` 都没有，
  只能以「本页为空」判断到底。用「本页条数」冒充总数会让分页**永远停在第一页**。
- **`date` 不是「第几天」**：周一..周日 = 1..7，**完结 = 0**（官方 `getWeekInfo` 把 JS 的
  `getDay()` 换算过来的），按直觉从 0 起数会整体错位一天。
- 分区 id **26** 是服务端约定的「连载更新」块，因此它走每周更新表而不是普通列表；
  `type` 取 `all` / `manga` / `hanman`。

## 无广告与隐私

**本应用无广告，而且这一点由代码审计和一个检查脚本共同保证，不是碰巧如此。**

官方客户端的广告**全部由客户端主动拉取**，来源只有两处：`ad_content_all`（按 `adKey` 取素材，
官方定义了 60 多个插槽）与 `advertise_all`（启动时的四封面全屏广告）。服务端不会把广告混进业务载荷 ——
实测 `promote` / `latest` / `search` / `album` / `comic_read` 五个响应里搜不到任何广告特征字段，
阅读流的图片也全部落在站点自己的 CDN 上。

因此「无广告」在这里等价于「不调用那两个接口、不实现任何插槽」，本应用正是这么做的，另外：

1. **网络层拦截第三方广告与追踪域名**（`AdBlocker`，覆盖广告交易/投放、行为分析、安装归因三类），
   拦截时返回合成的 403 空响应而不是抛异常 —— 抛异常会让「被拦截」与「网络故障」在上层混为一谈。
2. **图片通道共用同一个 OkHttpClient**。Coil 若不显式传入客户端会自建一个默认实例，
   那样拦截器就只覆盖 API 流量，图片通道是敞开的。
3. **不做任何行为采集**，也不引入第三方埋点。
4. **凭证加密落盘**：官方把 JWT 明文放在 Web 存储里；Android 上应用私有目录虽有沙箱保护，
   但设备被 root、被取证、或发生备份提取时明文 token 可直接复用。本应用用 Android Keystore 中的
   AES-256 密钥加密后落盘，解密失败则清掉该条目并视为未登录。

验证方式：

```bash
./scripts/check-no-ads.sh      # 源码出现广告接口/插槽/素材字段即失败
```

脚本会先剥掉注释再匹配 —— 因为 `AdBlocker` 的说明里必须写明这些接口与字段名，
否则「防的是什么」无从理解。设置页的「隐私与广告」栏会显示当前屏蔽的域名类数。

## 账号与收藏：三个容易搞反的地方

| 用途 | 方法与路径 | 参数 |
| --- | --- | --- |
| 登录 | POST `login` | `username`, `password` → 响应 `data.jwttoken` + 会员信息 |
| 收藏列表 | GET `favorite` | `page`, `folder_id`, `o` |
| **收藏切换** | POST `favorite` | `aid` |
| 收藏夹编辑 | POST `favorite_folder` | `type`(`add`/`edit`/`move`/`del`), `folder_id`, `folder_name`, `aid` |
| 观看历史 | GET `watch_list` | `page` |
| **删除单条历史** | POST `watch_list` | `id` |

1. **收藏是一个「切换」接口。** POST `favorite {aid}` 既可收藏也可取消，由响应的 `type`
   告知实际发生了什么（`add`/`remove`/`move`/`edit`）。所以界面状态应以响应为准，
   而不是本地取反 —— 这样即使本地状态早已过时，也会被纠正回真实状态。
2. **`POST watch_list` 是删除历史，不是记录观看。** 极易搞反：官方代码里唯一的调用点是
   删除历史的菜单项，观看记录由**服务端在读取章节时自动写入**。误当成「上报观看」的话，
   表现就是每读一话删掉一条历史。
3. **`like` 的响应是嵌套封套。** 外层 `code == 200` 只表示请求成功，业务结果还在 `data` 里
   再套一层 `{code, status, msg}`。只看外层会把失败当成功。

**阅读入口要覆盖三种作品形态**：多章节（续读或第一话）、单章节（该项本身）、
**无章节**（`series` 为空时用作品 id 自身调 `comic_read`）。最后一种最容易漏 ——
若阅读入口只从 `series` 里找章节，这类作品会完全没有开始阅读的入口。

**阅读进度存在本地**（服务端的历史只到作品粒度）：记录「作品 → 上次那一话」，
且只在那一话仍存在于目录里时才提供「继续阅读」。

## 已知限制

- **评论只读**：不发帖、不投票 —— 那需要一个被服务端信任的账号去发内容，而本客户端的定位是阅读。
- **没有离线下载**：阅读需要网络。
- **检查更新有意不做成更新提示**：`setting` 回的 `jm3_version` 指的是**官方 App** 的版本，
  与本应用没有可比性；本应用把它用作协议对齐的探针（显示在「我的 → 服务端」）。
- **不硬性执行官方的 1 小时过期**：那是客户端对服务端行为的猜测，token 的真实有效期由服务端决定。
  照抄会出现「用着用着突然要重新登录」，而那个 token 可能本来还有效。
- 其他有意保留的取舍与已知风险（含页面内存实测、若干未做的改进）记在
  [`docs/engineering-notes.md`](docs/engineering-notes.md)。

## 许可

见 [LICENSE](LICENSE)。本项目仅用于学习与个人使用；不提供任何担保。

## 致谢与署名

- **设计与视觉系统**来自站主的博客 [moyingyilang.github.io](https://moyingyilang.github.io)；
  官方客户端与反编译还原的源码只用于协议分析，本仓库不含其任何代码。
- **协议逆向的结论、Kotlin 重写与本文档**，由 *DeepSeek Harness* 上的编码 agent
  （模型 `deepseek-v4-flash`）在站主的指导与授权下完成与维护。
  过程记录（修过什么、为什么、哪些坑别再踩）在 [`docs/engineering-notes.md`](docs/engineering-notes.md)。
