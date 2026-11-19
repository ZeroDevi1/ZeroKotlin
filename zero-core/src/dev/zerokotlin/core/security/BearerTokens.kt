package dev.zerokotlin.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

/** 生成 Bearer 令牌、计算会话摘要，并解析请求携带的令牌。 */
object BearerTokens {
    /** 生成令牌字节所用的安全随机数生成器。 */
    private val random = SecureRandom()

    /** 接受 43 个 Base64 URL 安全字符，与 32 字节去填充后的编码长度一致。 */
    private val format = Regex("[A-Za-z0-9_-]{43}")

    /**
     * 将 32 个安全随机字节编码为无填充的 URL 安全 Base64。
     *
     * @return 交给客户端使用的 43 字符原始令牌。
     */
    fun issue(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

    /**
     * 对原始令牌的 US-ASCII 字节计算 SHA-256，用于会话存储和查询。
     *
     * @param raw 客户端使用的原始令牌。
     * @return 64 字符的小写十六进制摘要。
     */
    fun digest(raw: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.US_ASCII)))

    /**
     * 解析 Bearer 请求头。方案名忽略大小写，后面须紧跟一个空格和大小写敏感的令牌。
     *
     * @param header 可缺省的 `Authorization` 请求头。
     * @return 格式匹配时返回原始令牌，请求头缺失或格式不符时返回 `null`。
     */
    internal fun fromAuthorization(header: String?): String? {
        if (header == null || !header.startsWith("Bearer ", ignoreCase = true)) return null
        return header.substring(7).takeIf(format::matches)
    }
}
