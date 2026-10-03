package com.jmcomic_next.lyqs.data.crypto

import okio.ByteString.Companion.decodeBase64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * JMComic 客户端协议中的加解密部分。
 *
 * 算法取自 `JMComic_SRC`（官方 App 反编译还原出的源码）中的
 * `api/HttpUtil.ts`、`api/ApiEndpointUtil.ts` 与 `utils/Function.js`，
 * 这里是逐项等价移植 —— 任何「顺手优化」都可能让服务端拒绝或解出乱码。
 *
 * 协议要点：
 *
 *  - 请求头 `Tokenparam: "<秒级时间戳>,<客户端版本>"`，`Token: md5(时间戳 + [TOKEN_SEED])`
 *  - **响应体的 AES 密钥就是 `Token` 头本身**：`md5(时间戳 + [TOKEN_SEED])`
 *    以 UTF-8 十六进制字符串形式作为 32 字节密钥，即 AES-256-ECB
 *  - 广告接口例外，密钥不带时间戳：`md5([TOKEN_SEED])`
 *  - 解不开时回退用 [TOKEN_SEED_ALT] 再试一次；两次都失败则整个 `data` 视为空
 *  - 主机发现接口用的是另一个固定密钥：`md5([HOST_SEED])`
 */
object JmCrypto {

    /** 主密钥种子（源码 `apiPaths.token`）。同时用于请求 Token 与响应解密。 */
    const val TOKEN_SEED = "185Hcomic3PAPP7R"

    /** 备用密钥种子（源码 `tryDecryption` 的 possibleKeys[1]）。 */
    const val TOKEN_SEED_ALT = "18comicAPPContent"

    /** 主机发现接口的固定密钥种子（源码 `Function.js` 的 `key`）。 */
    const val HOST_SEED = "diosfjckwpqpdfjkvnqQjsik"

    /** 十六进制小写 MD5，与 npm `md5` 包的输出一致。 */
    fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()

    /** `Token` 请求头。注意它同时就是响应的 AES 密钥。 */
    fun token(timeSeconds: Long, seed: String = TOKEN_SEED): String = md5("$timeSeconds$seed")

    /** `Tokenparam` 请求头。 */
    fun tokenParam(timeSeconds: Long, version: String): String = "$timeSeconds,$version"

    /**
     * AES-256-ECB 解密。
     *
     * @param cipherBase64 密文的 Base64 文本（服务端直接把它当响应体返回）
     * @param keyHex 32 个字符的十六进制密钥字符串，按 UTF-8 取字节即 32 字节密钥
     * @return 明文；密钥不对或数据损坏时返回 null（对应 CryptoJS 抛异常的那条路径）
     */
    fun decryptEcb(cipherBase64: String, keyHex: String): String? = try {
        val key = SecretKeySpec(keyHex.toByteArray(Charsets.UTF_8), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key)
        val raw = sanitizeBase64(cipherBase64).decodeBase64()?.toByteArray() ?: return null
        val plain = cipher.doFinal(raw)
        String(plain, Charsets.UTF_8)
    } catch (t: Throwable) {
        null
    }

    /**
     * 把响应体清理成纯净的 Base64。
     *
     * 必需而不是洁癖：主机清单托管在对象存储上，返回的文本**开头带 UTF-8 BOM**（U+FEFF），
     * 底层的 Base64 解码器遇到非字母表字符会失败（这里用 okio，Android 与桌面都可用）。
     * 换行与空格同样会被清掉（CryptoJS 的宽松解析掩盖了这一点，移植到 Android 就暴露了）。
     */
    private fun sanitizeBase64(text: String): String = buildString(text.length) {
        for (c in text) {
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/' || c == '=') {
                append(c)
            }
        }
    }

    /**
     * 解密业务接口的 `data` 字段，对应源码的 `tryDecryption`。
     *
     * 依次尝试两个种子，第一个能解出合法 JSON 的胜出；都失败返回 null
     * （源码此时会把 `response.data` 置为空字符串，由调用方当作「无数据」处理）。
     *
     * 有意省略的一处：源码对**广告接口**用不带时间戳的密钥（`md5(seed)`）。
     * 本应用不请求任何广告接口，那条分支不可达，因此没有移植 ——
     * 保留一段只为「永不调用」的功能服务的代码是负债。
     * 相关背景见 [com.jmcomic_next.lyqs.data.remote.AdBlocker]。
     */
    fun decryptApiData(dataBase64: String, timeSeconds: Long): String? {
        // 优先返回 JSON 形态（那说明密钥对了），但**不能只认 JSON**：
        // 这个服务端有些接口的 data 就是一句人话 —— 实测 `album_sertracking` 回的是
        // "已追踪!" / "已取消追踪!"。把这种当「解密失败」会让上层把请求**重发一遍**，
        // 而非幂等的 POST 重发一次就是把刚做的操作撤销掉。
        var messageLike: String? = null
        for (seed in listOf(TOKEN_SEED, TOKEN_SEED_ALT)) {
            val key = md5("$timeSeconds$seed")
            val trimmed = decryptEcb(dataBase64, key)?.trim() ?: continue
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed
            if (messageLike == null) messageLike = trimmed
        }
        return messageLike
    }

    /**
     * 解密主机发现接口的响应体。
     *
     * 返回 `{"Server": ["host", ...], "jm3_Server": "..."}` 形式的 JSON 文本。
     */
    fun decryptHostPayload(encryptedText: String): String? {
        val plain = decryptEcb(encryptedText, md5(HOST_SEED)) ?: return null
        val trimmed = plain.trim()
        return if (trimmed.startsWith("{")) trimmed else null
    }

    /**
     * 图片被切成的份数，源码 `Function.js` 的 `get_num`。
     *
     * 份数由 `md5(aid + page)` 的最后一个字符决定；当 aid 落在特定区间时先对该字符码取模，
     * 目的是让不同时期的漫画使用不同的切分密度。
     *
     * 注意 aid 小于 268850 时既不取模也匹配不到任何 case，份数保持 10 —— 这是源码的行为，
     * 不是遗漏。
     *
     * @param page 页码，必须是接口原样给出的字符串（"1" 与 "01" 会得到不同结果）
     */
    fun sliceCount(aid: Int, page: String): Int {
        var key = md5("$aid$page").last().code
        if (aid in 268850..421925) {
            key %= 10
        } else if (aid >= 421926) {
            key %= 8
        }
        return when (key) {
            0 -> 2
            1 -> 4
            2 -> 6
            3 -> 8
            4 -> 10
            5 -> 12
            6 -> 14
            7 -> 16
            8 -> 18
            9 -> 20
            else -> 10
        }
    }

    /**
     * 该图片是否需要还原切片。
     *
     * 源码 `scramble_image` 开头：GIF 不切；`aid < scramble_id` 的老漫画也不切。
     */
    fun needsUnscramble(url: String, aid: Int, scrambleId: Int): Boolean {
        if (url.contains(".gif")) return false
        return aid >= scrambleId
    }
}
