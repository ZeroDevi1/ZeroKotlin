package dev.zerokotlin.ktor.system.account

import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.core.security.issueToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.Locale

private val usernamePattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{2,63}")

internal fun username(value: String) = value.takeIf(usernamePattern::matches)?.lowercase(Locale.ROOT)
private fun acceptablePassword(value: String) = value.length in 1..72 && value.toByteArray().size <= 72
private fun validNewPassword(value: String) = acceptablePassword(value) && value.codePointCount(0, value.length) >= 12

private val passwords = BCryptPasswordEncoder(12)
private val dummyHash = hash(issueToken())

/** 使用与既有数据库兼容的 BCrypt 编码，计算过程在获取系统写锁之前执行。 */
internal suspend fun accountPassword(password: String): String = withContext(Dispatchers.IO) {
    if (!validNewPassword(password)) throw SystemFailure(400, "密码至少 12 个字符且最多 72 个 UTF-8 字节")
    hash(password)
}

internal fun matchesLoginPassword(password: String, hash: String?): Boolean =
    acceptablePassword(password) && passwords.matches(password, hash ?: dummyHash)

internal fun newPasswordHash(oldPassword: String, newPassword: String, currentHash: String): String {
    if (!validNewPassword(newPassword)) throw SystemFailure(400, "密码至少 12 个字符且最多 72 个 UTF-8 字节")
    if (!acceptablePassword(oldPassword) || !passwords.matches(oldPassword, currentHash)) {
        throw SystemFailure(400, "旧密码不正确")
    }
    return hash(newPassword)
}

private fun hash(password: String) = checkNotNull(passwords.encode(password))
