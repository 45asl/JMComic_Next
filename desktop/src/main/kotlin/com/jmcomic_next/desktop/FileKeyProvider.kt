package com.jmcomic_next.desktop

import com.jmcomic_next.lyqs.data.auth.SecretKeyProvider
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * [SecretKeyProvider] 的桌面实现：AES 密钥存放在用户配置目录下的文件里。
 *
 * **强度说明（如实写清）**：这比不上 Android 的 Keystore —— 那里密钥不导出、
 * 由硬件或系统保护；这里密钥就是一串字节，能被读到该文件的进程取走。
 * 桌面端能做到的合理水平是「限制文件权限（仅本人可读）+ 与数据分离存放」，
 * 因此这里显式把权限设为 600。要更强的保护需要系统钥匙串（libsecret / DPAPI），
 * 那是后续可以替换的实现 —— 接口就是为这种替换准备的。
 */
class FileKeyProvider(private val dir: File) : SecretKeyProvider {

    override fun aesKey(alias: String): SecretKey? = runCatching {
        val file = File(dir, "$alias.key")
        if (file.exists() && file.length() > 0) {
            SecretKeySpec(file.readBytes(), "AES")
        } else {
            val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            dir.mkdirs()
            file.writeBytes(key.encoded)
            // 仅本人可读写（在支持 POSIX 权限的平台上生效）
            file.setReadable(false, false); file.setReadable(true, true)
            file.setWritable(false, false); file.setWritable(true, true)
            key
        }
    }.getOrNull()
}
