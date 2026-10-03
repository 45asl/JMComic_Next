package com.jmcomic_next.lyqs.data.auth

import com.jmcomic_next.lyqs.data.remote.dto.MemberInfo

/** 供界面观察的登录态快照。放在跨平台模块里，Android 与桌面共用。 */
data class AuthState(
    val loggedIn: Boolean = false,
    val member: MemberInfo? = null,
)
