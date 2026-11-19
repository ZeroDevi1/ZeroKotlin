package dev.zerokotlin.core.security

import org.springframework.security.crypto.password.PasswordEncoder

/**
 * 对密码进行哈希，以便仅在编码成功后写入凭证。
 *
 * @receiver 应用配置的密码编码器。
 * @param rawPassword 待编码的明文密码。
 * @return 可持久化的密码哈希。
 * @throws IllegalStateException 编码器返回 `null`。
 */
fun PasswordEncoder.hashPassword(rawPassword: String): String =
    checkNotNull(encode(rawPassword)) { "Password encoder returned no hash" }
