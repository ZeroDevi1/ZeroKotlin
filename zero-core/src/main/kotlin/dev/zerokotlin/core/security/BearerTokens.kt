package dev.zerokotlin.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

object BearerTokens {
    private val random = SecureRandom()
    private val format = Regex("[A-Za-z0-9_-]{43}")

    fun issue(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

    fun digest(raw: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.US_ASCII)))

    internal fun fromAuthorization(header: String?): String? {
        if (header == null || !header.startsWith("Bearer ", ignoreCase = true)) return null
        return header.substring(7).takeIf(format::matches)
    }
}
