package com.jmcomic_next.desktop

import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.auth.AuthStore
import com.jmcomic_next.lyqs.data.auth.SecureStore
import com.jmcomic_next.lyqs.data.prefs.BlockStore

import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 桌面端连通性冒烟程序（无界面）。
 *
 * 存在的意义：在写界面之前，先用最小代价证明**跨平台数据层在真实网络下能跑通** ——
 * 登录、拉首页列表。如果这一步不通，界面写得再多也没用。
 *
 * 凭据从环境变量读取（JM_USER / JM_PASS），不从命令行参数读：
 * 命令行参数会出现在进程列表里，也容易被打进日志。
 *
 * 用法：JM_USER=... JM_PASS=... gradle smoke
 */
// 显式声明 : Unit：块末尾的 exitProcess 返回 Nothing，
// 表达式体会因此把整个 main 推断成 Nothing 而报错
fun main(args: Array<String>): Unit = runBlocking {
    val home = System.getProperty("user.home")
    val configDir = File(home, ".config/jmcomic-next")
    println("[冒烟] 配置目录：$configDir")

    // 与 Android 侧同一套数据层，只是换了三个平台实现
    val secure = SecureStore(
        prefs = PreferencesKeyValueStore("jm_secure"),
        keys = FileKeyProvider(File(configDir, "keys")),
    )
    val auth = AuthStore(secure)

    val repo = JmRepository.create(
        authStore = auth,
        blockStore = BlockStore(PreferencesKeyValueStore("jm_block")),
        debug = true,
    )

    // 启动时先 bootstrap：它做主机发现，并从配置接口取回**图床主机**。
    // 缺了后者的后果是所有封面都加载不出来 —— 而这条路径在 Android 上是隐式发生的。
    repo.bootstrap()
    println("[冒烟] 引导完成（主机发现 + 图床主机）")

    val user = System.getenv("JM_USER")
    val pass = System.getenv("JM_PASS")
    if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
        val member = repo.login(user, pass)
        println("[冒烟] 登录成功：${member.username ?: "(无用户名)"} uid=${member.uid ?: "?"} 等级=${member.levelName ?: "?"}")
    } else {
        println("[冒烟] 未提供 JM_USER / JM_PASS，跳过登录，只测公开接口")
    }
    println("[冒烟] 会话状态：loggedIn=${auth.isLoggedIn}")

    val page = repo.latest(1)
    println("[冒烟] 首页第一页：${page.items.size} 条 / 服务端共 ${page.total} 条 / 被屏蔽规则挡掉 ${page.hidden} 条")
    page.items.take(5).forEach { println("    - ${it.name ?: "(无标题)"} · ${it.author ?: "?"}") }

    println("[冒烟] 封面地址示例：${repo.coverUrl(page.items.first())}")
    println("[冒烟] 结束")
    kotlin.system.exitProcess(0)
}
