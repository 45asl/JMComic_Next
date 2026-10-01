package com.jmcomic_next.lyqs.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 加密的键值存储，用于保存会话凭证。
 *
 * **为什么不用普通 SharedPreferences**：官方客户端把 JWT 明文放在 Web 存储里
 * （`localStorage.setItem("jwttoken", ...)`），那是一个能被同源脚本读到的位置。
 * Android 上应用私有目录本来就有沙箱保护，但设备被 root、被取证、或发生备份提取时，
 * 明文 token 就是可直接复用的凭据 —— 而它的有效期正是「直到服务端撤销」。
 * 因此这里用 Android Keystore 里的 AES-256 密钥加密后再落盘：
 * 密钥由系统保管（有硬件支持时不出安全元件），应用只持有密文。
 *
 * 密文格式：`[iv 长度 1 字节][iv][ciphertext + GCM tag]`，整体再做 Base64。
 * 把 IV 长度写进去而不是写死 12，是为了将来换算法时不会静默读错。
 *
 * 解密失败时**返回 null 并清空该条目**而不是抛异常：
 * Keystore 密钥可能因为系统还原、锁屏凭据变更等原因失效，
 * 那种情况下正确行为是「当作未登录，让用户重新登录」，而不是崩溃。
 */
class SecureStore(
    context: Context,
    private val alias: String = DEFAULT_ALIAS,
) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun put(key: String, value: String?) {
        if (value == null) {
            prefs.edit { remove(key) }
            return
        }
        val packed = runCatching { encrypt(value) }.getOrNull()
        if (packed == null) {
            // 加密不可用时宁可只保留在内存里，也不落明文
            prefs.edit { remove(key) }
            return
        }
        prefs.edit { putString(key, packed) }
    }

    fun get(key: String): String? {
        val packed = prefs.getString(key, null) ?: return null
        val plain = runCatching { decrypt(packed) }.getOrNull()
        if (plain == null) {
            // 解不开说明密钥已失效，清掉这条脏数据，避免每次启动都失败一次
            prefs.edit { remove(key) }
        }
        return plain
    }

    fun remove(key: String) = prefs.edit { remove(key) }

    fun clear() = prefs.edit { clear() }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val out = ByteArray(1 + iv.size + body.size)
        out[0] = iv.size.toByte()
        iv.copyInto(out, 1)
        body.copyInto(out, 1 + iv.size)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(packed: String): String {
        val bytes = Base64.decode(packed, Base64.NO_WRAP)
        require(bytes.size > 1) { "密文过短" }
        val ivLen = bytes[0].toInt()
        require(ivLen in 1..32 && bytes.size > 1 + ivLen) { "IV 长度非法" }
        val iv = bytes.copyOfRange(1, 1 + ivLen)
        val body = bytes.copyOfRange(1 + ivLen, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // 不要求用户认证：这是「保持登录」的场景，弹指纹会让每次冷启动都要解锁一次
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val PREFS_NAME = "jm_secure"
        const val DEFAULT_ALIAS = "jm_session_v1"
    }
}
