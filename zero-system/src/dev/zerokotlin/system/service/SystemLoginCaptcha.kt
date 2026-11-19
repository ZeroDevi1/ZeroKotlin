package dev.zerokotlin.system.service

import dev.zerokotlin.core.security.BearerTokens
import dev.zerokotlin.core.security.LoginChallengeStore
import dev.zerokotlin.system.dto.LoginCaptcha
import dev.zerokotlin.system.security.LoginCaptchaImage
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Locale
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/** 签发一次性登录挑战，并在密码校验前核销提交的验证码。 */
@Service
class SystemLoginCaptcha(
    private val challenges: LoginChallengeStore,
    @param:Value("\${zero.system.captcha.enabled:false}") private val enabled: Boolean,
    @param:Value("\${zero.system.captcha.ttl-seconds:120}") private val ttlSeconds: Long,
) {
    private val random = SecureRandom()
    private val idFormat = Regex("[A-Za-z0-9_-]{43}")
    private val codeFormat = Regex("[A-Za-z0-9]{6}")

    init {
        require(ttlSeconds in 15..600) { "zero.system.captcha.ttl-seconds 必须介于 15 和 600 之间" }
    }

    /** 启用时签发 PNG 和一次性 ID，关闭时返回 null。 */
    fun issue(): LoginCaptcha? {
        if (!enabled) return null
        val alphabet = LoginCaptchaImage.alphabet
        val code = CharArray(6) { alphabet[random.nextInt(alphabet.length)] }.concatToString()
        val id = BearerTokens.issue()
        val png = LoginCaptchaImage.render(code, random)
        val ttl = Duration.ofSeconds(ttlSeconds)
        val expiresAt = System.currentTimeMillis() + ttl.toMillis()
        challenges.save(id, digest(id, code), ttl)
        return LoginCaptcha(id, png, expiresAt)
    }

    /**
     * 启用时按 ID 先取出并删除挑战，再比较答案。使用有效 ID 提交错误答案也会核销该挑战。
     * 关闭时允许旧客户端只提交账号和密码。
     */
    fun verify(id: String?, code: String?): Boolean {
        if (!enabled) return true
        if (id == null || !idFormat.matches(id)) return false
        val expected = challenges.consume(id) ?: return false
        if (code == null || !codeFormat.matches(code)) return false
        val supplied = digest(id, code.uppercase(Locale.ROOT))
        return MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), supplied.toByteArray(Charsets.US_ASCII))
    }

    /** 将摘要绑定至挑战 ID。 */
    private fun digest(id: String, code: String): String = BearerTokens.digest("$id:$code")
}
