package dev.zerokotlin.system.service

import dev.zerokotlin.core.security.BearerTokens
import dev.zerokotlin.core.security.hashPassword
import dev.zerokotlin.system.dto.LoginRequest
import dev.zerokotlin.system.dto.LoginResponse
import dev.zerokotlin.system.entity.LoginAuditRecord
import dev.zerokotlin.system.entity.SessionRecord
import dev.zerokotlin.system.entity.UserRecord
import dev.zerokotlin.system.mapper.LoginAuditMapper
import dev.zerokotlin.system.mapper.SessionMapper
import dev.zerokotlin.system.mapper.UserMapper
import dev.zerokotlin.system.security.currentSystemActor
import dev.zerokotlin.system.support.acceptableLoginPassword
import dev.zerokotlin.system.support.newId
import dev.zerokotlin.system.support.normalizeUsername
import dev.zerokotlin.system.support.query
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SystemAuthentication(
    private val users: UserMapper,
    private val passwords: PasswordEncoder,
    private val transactions: SystemSessionTransactions,
    private val loginAudit: LoginAuditMapper,
) {
    private val dummyHash = passwords.hashPassword(BearerTokens.issue())

    fun login(request: LoginRequest, address: String): LoginResponse? {
        val username = normalizeUsername(request.username)
        val user = username?.let { users.selectOne(query<UserRecord>().eq("username", it)) }
        val matches = acceptableLoginPassword(request.password) &&
            passwords.matches(request.password, user?.passwordHash ?: dummyHash)
        val result = if (matches && user != null && user.enabled == 1) transactions.open(user, address) else null
        if (result == null) {
            loginAudit.insert(LoginAuditRecord(newId(), user?.id, username, 0, address, System.currentTimeMillis()))
        }
        return result
    }
}

@Service
class SystemSessionTransactions(
    private val writes: SystemWrites,
    private val users: UserMapper,
    private val sessions: SessionMapper,
    private val loginAudit: LoginAuditMapper,
    @param:Value("\${zero.system.session.seconds:28800}") private val sessionSeconds: Long,
) {
    init {
        require(sessionSeconds in 1..604800) { "zero.system.session.seconds 必须介于 1 和 604800 之间" }
    }

    @Transactional
    fun open(verifiedUser: UserRecord, address: String): LoginResponse? {
        writes.lock()
        val user = users.selectById(verifiedUser.id) ?: return null
        if (user.enabled != 1 || user.passwordHash != verifiedUser.passwordHash) return null

        val now = System.currentTimeMillis()
        val expiresAt = now + sessionSeconds * 1_000
        val token = BearerTokens.issue()
        sessions.delete(query<SessionRecord>().le("expires_at", now))
        sessions.insert(SessionRecord(BearerTokens.digest(token), user.id, expiresAt))
        loginAudit.insert(LoginAuditRecord(newId(), user.id, user.username, 1, address, now))
        return LoginResponse(token, "Bearer", expiresAt)
    }

    @Transactional
    fun logout() {
        // 删除单个令牌摘要，保留用户在其他客户端的会话。
        val actor = currentSystemActor()
        writes.lock()
        if (sessions.deleteById(actor.sessionDigest) == 1) {
            writes.record(actor, "auth.logout", actor.user.id)
        }
    }
}
