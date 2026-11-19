package dev.zerokotlin.core.security

import org.springframework.security.crypto.password.PasswordEncoder

/** 只有编码成功才允许将凭证写入数据库。 */
fun PasswordEncoder.hashPassword(rawPassword: String): String =
    checkNotNull(encode(rawPassword)) { "Password encoder returned no hash" }
