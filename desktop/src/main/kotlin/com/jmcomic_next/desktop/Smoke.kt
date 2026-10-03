package com.jmcomic_next.desktop

import com.jmcomic_next.lyqs.data.JmRepository
import com.jmcomic_next.lyqs.data.auth.AuthStore
import com.jmcomic_next.lyqs.data.auth.SecureStore
import com.jmcomic_next.lyqs.data.prefs.BlockStore
import com.jmcomic_next.lyqs.data.remote.JmHostDiscovery
import com.jmcomic_next.lyqs.data.remote.JmSession
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

    // 显式做主机发现再建仓储。
    //
    // 这一条是冒烟测试逼出来的：仓储内部只在**列表类请求**前懒执行发现，
    // 而 login() 不经过那条路径。Android 上不容易暴露（用户总是先看到首页，
    // 那时发现已经发生），但桌面端若先显示登录页就会直接报「API 主机尚未初始化」。
    val session = JmSession()
    val host = JmHostDiscovery.discover(session)
    println("[冒烟] 主机发现：${host ?: "(失败，全部入口不可用)"}")

    val repo = JmRepository.create(
        authStore = auth,
        session = session,
        blockStore = BlockStore(PreferencesKeyValueStore("jm_block")),
        debug = true,
    )

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

    println("[冒烟] 结束")
    kotlin.system.exitProcess(0)
}
