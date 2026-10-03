package com.jmcomic_next.lyqs.data.auth

/**
 * 远端层需要的最小登录态视图（2.0.0 起）。
 *
 * 远端层原本直接持有 Android 侧的 `AuthStore` 具体类，那样就没法搬进跨平台模块。
 * 抽成接口后，Android 由 `AuthStore` 实现，桌面端由自己的凭据存储实现，
 * 而远端层只依赖这三个成员：请求时取 token、401 时判断并清除登录态。
 */
interface AuthSession {
    /** 当前 JWT；未登录为 null。 */
    val token: String?

    /** 是否处于已登录状态（token 非空即视为已登录）。 */
    val isLoggedIn: Boolean

    /** 登录态失效时清除本地凭据。 */
    fun clear()
}
