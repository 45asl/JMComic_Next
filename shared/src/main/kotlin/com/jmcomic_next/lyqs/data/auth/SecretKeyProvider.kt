package com.jmcomic_next.lyqs.data.auth

import javax.crypto.SecretKey

/**
 * 对称密钥的来源（2.0.0 起）。
 *
 * [SecureStore] 的加解密逻辑与打包格式都是平台无关的，唯一碰平台能力的地方是
 * **密钥从哪来**：Android 用 Keystore（硬件保护、不导出），桌面只能用
 * 文件里存放的密钥再配合系统权限保护。把这一处抽成接口，其余部分就能共用。
 *
 * 返回 null 表示该平台暂时拿不到密钥 —— 调用方会**拒绝落盘**而不是退化成明文存储。
 */
interface SecretKeyProvider {
    /** 取（必要时创建）指定别名的 AES-256 密钥；不可用时返回 null。 */
    fun aesKey(alias: String): SecretKey?
}
