package com.jmcomic_next.lyqs.data.auth

import android.content.Context
import com.jmcomic_next.lyqs.data.remote.JmJson
import com.jmcomic_next.lyqs.data.remote.dto.MemberInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 账号会话状态。
 *
 * 只负责「持有与持久化」凭证；发请求、调接口都在仓储层。
 * token 与会员信息都经 [SecureStore] 加密落盘。
 *
 * **关于过期**：官方客户端在本地记了一个 1 小时的过期时间
 * （`saveAuthData` 里 `Date.now() + 60 * 60 * 1000`），到点就静默登出。
 * 这里**不硬性执行这个 1 小时上限** —— 那是客户端的猜测，而 token 的真实有效期
 * 由服务端决定。正确做法是：正常使用，直到服务端明确拒绝（401/凭证失效）再登出。
 * 否则会在 token 仍然有效时把用户踢出去，表现就是「用着用着突然要重新登录」。
 * 服务端拒绝的处理见 [com.jmcomic_next.lyqs.data.JmRepository] 的会话失效回调。
 */
/** 供界面观察的登录态快照。 */
data class AuthState(
    val loggedIn: Boolean = false,
    val member: MemberInfo? = null,
)

class AuthStore(context: Context, private val secure: SecureStore = SecureStore(context)) : AuthSession {

    /**
     * 缓存与锁。
     *
     * 这两件事必须在**多个线程**之间成立：读 token 的是 OkHttp 的请求线程（拦截器每发一个
     * 请求就要取一次），写它的是界面线程（登录/登出）或请求线程（401 时清会话）。
     * 普通字段在这里会漏：一个刚登录的请求可能读到 `null`（于是没带 Authorization），
     * 或 `ensureLoaded` 被两个线程同时跑一遍。用一把最简的锁把「读-改-写」圈起来，
     * 而不是引入更复杂的机制 —— 这里的临界区只有赋值与一次加解密。
     */
    private val lock = Any()
    private var cachedToken: String? = null
    private var cachedMember: MemberInfo? = null
    private var loaded = false

    /**
     * 登录态。做成可观察的，是因为它会**被动变化** ——
     * 服务端拒绝凭证时拦截器会清掉会话（见 `JmRemote`），
     * 此时「我的」页需要立刻反映为未登录，而不是等用户下次手动刷新。
     */
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private fun publish() {
        _state.value = AuthState(
            loggedIn = !cachedToken.isNullOrBlank(),
            member = cachedMember,
        )
    }

    /** 当前 JWT。为空即未登录。 */
    override val token: String? get() = synchronized(lock) {
        ensureLoadedLocked()
        cachedToken
    }

    val member: MemberInfo? get() = synchronized(lock) {
        ensureLoadedLocked()
        cachedMember
    }

    override val isLoggedIn: Boolean get() = !token.isNullOrBlank()

    /** 登录/注册成功后保存会话。 */
    fun save(token: String, member: MemberInfo?) = synchronized(lock) {
        cachedToken = token
        cachedMember = member
        loaded = true
        secure.put(KEY_TOKEN, token)
        secure.put(KEY_MEMBER, member?.let { JmJson.encodeToString(MemberInfo.serializer(), it) })
        publish()
    }

    /** 更新会员信息（例如刷新后拿到的余额/等级），不动 token。 */
    fun updateMember(member: MemberInfo?) = synchronized(lock) {
        cachedMember = member
        secure.put(KEY_MEMBER, member?.let { JmJson.encodeToString(MemberInfo.serializer(), it) })
        publish()
    }

    /**
     * 登出。
     *
     * 先清内存再清磁盘：即使磁盘清理失败，本次进程内也已经是未登录状态，
     * 不会出现「界面说登出了但请求仍带着旧 token」这种最糟的中间态。
     */
    override fun clear() = synchronized(lock) {
        cachedToken = null
        cachedMember = null
        loaded = true
        secure.remove(KEY_TOKEN)
        secure.remove(KEY_MEMBER)
        publish()
    }

    /** 首次访问时从加密存储读一次。调用方必须已持有 [lock]。 */
    private fun ensureLoadedLocked() {
        if (loaded) return
        loaded = true
        cachedToken = secure.get(KEY_TOKEN)
        cachedMember = secure.get(KEY_MEMBER)?.let {
            runCatching { JmJson.decodeFromString(MemberInfo.serializer(), it) }.getOrNull()
        }
        publish()
    }

    private companion object {
        const val KEY_TOKEN = "jwt"
        const val KEY_MEMBER = "member"
    }
}
