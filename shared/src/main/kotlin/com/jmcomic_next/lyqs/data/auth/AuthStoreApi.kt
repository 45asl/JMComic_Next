package com.jmcomic_next.lyqs.data.auth

import com.jmcomic_next.lyqs.data.remote.dto.MemberInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * 登录态存储的完整接口（2.0.0 起）。
 *
 * 分成两层是有意的：[AuthSession] 是**远端层**需要的最小视图（token / isLoggedIn / clear），
 * 而这里多出来的 `state`、`member`、`save`、`updateMember` 是**仓储层与界面**需要的。
 * 依赖方按自己真正需要的那一层来声明，桌面端实现时也就知道自己必须提供什么。
 */
interface AuthStoreApi : AuthSession {
    /** 供界面观察的登录态。 */
    val state: StateFlow<AuthState>

    /** 当前会员信息；未登录为 null。 */
    val member: MemberInfo?

    /** 登录成功后保存凭证与会员信息。 */
    fun save(token: String, member: MemberInfo?)

    /** 只更新会员信息（例如刷新用户资料后）。 */
    fun updateMember(member: MemberInfo?)
}
