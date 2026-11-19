package dev.zerokotlin.core.security

import org.springframework.security.core.Authentication

/** 根据 Bearer 令牌摘要解析当前会话，由系统模块提供实现。 */
fun interface BearerSessionAuthentication {
    /**
     * 查找并校验摘要对应的会话。
     *
     * @param sessionDigest 原始令牌经 [BearerTokens.digest] 计算的 SHA-256 十六进制摘要。
     * @return 会话有效时返回认证主体及其权限信息，会话无效时返回 `null`。
     */
    fun authenticate(sessionDigest: String): Authentication?
}
