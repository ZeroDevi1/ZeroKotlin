package dev.zerokotlin.core.security

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Locale

@Serializable
data class LoginCaptcha(val id: String, val imageBase64: String, val expiresAt: Long)

class LoginCaptchaService(private val enabled: Boolean, ttlSeconds: Long, private val state: SecurityState) {
    private val random = SecureRandom()
    private val ttl = Duration.ofSeconds(ttlSeconds.also { require(it in 15..600) })
    private val idPattern = Regex("[A-Za-z0-9_-]{43}")
    private val codePattern = Regex("[A-Za-z0-9]{6}")

    suspend fun issue(): LoginCaptcha? {
        if (!enabled) return null
        val alphabet = LoginCaptchaImage.alphabet
        val answer = CharArray(6) { alphabet[random.nextInt(alphabet.length)] }.concatToString()
        val id = issueToken()
        val png = LoginCaptchaImage.render(answer, random)
        val expiresAt = System.currentTimeMillis() + ttl.toMillis()
        state.save(id, digest("$id:$answer"), ttl)
        return LoginCaptcha(id, png, expiresAt)
    }

    suspend fun verify(id: String?, code: String?): Boolean {
        if (!enabled) return true
        if (id == null || !idPattern.matches(id)) return false
        // 即使答案错误也单次核销；同一挑战不能并发复用。
        val expected = state.consume(id) ?: return false
        if (code == null || !codePattern.matches(code)) return false
        val supplied = digest("$id:${code.uppercase(Locale.ROOT)}")
        return MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), supplied.toByteArray(Charsets.US_ASCII))
    }
}
