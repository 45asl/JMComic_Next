package com.jmcomic_next.lyqs.data.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * [SecretKeyProvider] 的 Android 实现 —— 密钥由系统 Keystore 持有，**不导出**。
 *
 * 不要求用户认证：这是「保持登录」的场景，弹指纹会让每次冷启动都要解锁一次。
 */
class AndroidKeystoreKeyProvider : SecretKeyProvider {

    override fun aesKey(alias: String): SecretKey? = runCatching {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return@runCatching it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        generator.generateKey()
    }.getOrNull()

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
    }
}
