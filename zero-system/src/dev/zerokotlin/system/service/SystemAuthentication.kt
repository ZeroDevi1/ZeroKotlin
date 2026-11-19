package dev.zerokotlin.system.service

import dev.zerokotlin.core.IpRegionLookup
import dev.zerokotlin.core.security.BearerTokens
import dev.zerokotlin.core.security.hashPassword
import dev.zerokotlin.system.dto.ChangePasswordRequest
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
import dev.zerokotlin.system.support.checkedPassword
import dev.zerokotlin.system.support.invalid
import dev.zerokotlin.system.support.missing
import dev.zerokotlin.system.support.newId
import dev.zerokotlin.system.support.normalizeUsername
import dev.zerokotlin.system.support.query
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * 验证登录凭据，在会话事务中记录成功登录，并在登录失败时单独写入审计。
 *
 * @property users 按规范化后的用户名查找账号及其密码哈希。
 * @property passwords 校验提交的密码是否与持久化哈希匹配。
 * @property transactions 在事务内复核账号并创建会话。
 * @property loginAudit 保存未能建立会话的登录尝试。
 * @property captcha 在启用时先核销登录挑战。
 * @property regions 离线查询登录地址的归属地。
 */
@Service
class SystemAuthentication(
    private val users: UserMapper,
    private val passwords: PasswordEncoder,
    private val transactions: SystemSessionTransactions,
    private val loginAudit: LoginAuditMapper,
    private val captcha: SystemLoginCaptcha,
    private val regions: IpRegionLookup,
) {
    /** 没有匹配账号时用于密码校验的占位哈希，让符合长度要求的密码仍经过哈希比对。 */
    private val dummyHash = passwords.hashPassword(BearerTokens.issue())

    /**
     * 先校验验证码，再校验密码及账号启用状态并尝试建立会话。认证失败时写入失败审计。
     *
     * @param request 用户提交的用户名、密码和验证码。
     * @param address 写入登录审计的远端地址，HTTP 入口最多传入 128 个字符。
     * @return 创建会话后返回 Bearer 令牌及过期时刻，认证失败或写入前账号已变化时返回 null。
     * @throws ResponseStatusException 验证码缺失、错误、过期或已核销时返回 HTTP 400。
     */
    fun login(request: LoginRequest, address: String): LoginResponse? {
        val username = normalizeUsername(request.username)
        val region = regions.find(address)
        if (!captcha.verify(request.captchaId, request.captchaCode)) {
            recordFailure(null, username, address, region)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码错误或已失效，请刷新后重试。")
        }
        val user = username?.let { users.selectOne(query<UserRecord>().eq("username", it)) }
        val matches = acceptableLoginPassword(request.password) &&
            passwords.matches(request.password, user?.passwordHash ?: dummyHash)
        val result = if (matches && user != null && user.enabled == 1) transactions.open(user, address, region) else null
        if (result == null) recordFailure(user?.id, username, address, region)
        return result
    }

    /** 写入失败尝试，保留已确认的账号 ID、规范化登录名和请求对端地址。 */
    private fun recordFailure(userId: String?, username: String?, address: String, region: String?) {
        loginAudit.insert(LoginAuditRecord(newId(), userId, username, 0, address, System.currentTimeMillis(), region))
    }

    /**
     * 用当前会话身份查找账号并校验旧密码。在取得写入锁前完成密码比对和哈希计算。
     *
     * @param request 当前密码和符合账号密码规则的新密码。
     */
    fun changePassword(request: ChangePasswordRequest) {
        val newPassword = checkedPassword(request.newPassword)
        val user = users.selectById(currentSystemActor().user.id) ?: missing("用户")
        if (!acceptableLoginPassword(request.oldPassword) || !passwords.matches(request.oldPassword, user.passwordHash)) {
            invalid("旧密码不正确")
        }
        transactions.changePassword(user, passwords.hashPassword(newPassword))
    }
}

/**
 * 在事务内创建和注销会话，并同步记录审计。
 *
 * @property writes 串行化会话创建和注销，记录注销操作。
 * @property users 获取写入锁之后重新读取账号，确认启用状态和密码哈希。
 * @property sessions 按令牌摘要写入和删除会话，清理过期记录。
 * @property loginAudit 写入成功登录审计。
 * @property sessionSeconds 会话有效期，单位为秒。允许 1～604800，默认为 28800。
 */
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

    /**
     * 在写入锁下再次核对账号，清理过期会话，将新会话摘要与成功审计一并提交。
     *
     * @param verifiedUser 密码已经验证的账号快照。
     * @param address 写入成功登录审计的远端地址。
     * @param region 有可用离线数据时记录地址的归属地。
     * @return Bearer 令牌及 Unix 毫秒级过期时间。账号不存在、已停用或密码哈希发生变化时返回 null。
     */
    @Transactional
    fun open(verifiedUser: UserRecord, address: String, region: String?): LoginResponse? {
        writes.lock()
        val user = users.selectById(verifiedUser.id) ?: return null
        if (user.enabled != 1 || user.passwordHash != verifiedUser.passwordHash) return null

        val now = System.currentTimeMillis()
        val expiresAt = now + sessionSeconds * 1_000
        val token = BearerTokens.issue()
        sessions.delete(query<SessionRecord>().le("expires_at", now))
        sessions.insert(SessionRecord(BearerTokens.digest(token), user.id, expiresAt))
        loginAudit.insert(LoginAuditRecord(newId(), user.id, user.username, 1, address, now, region))
        return LoginResponse(token, "Bearer", expiresAt)
    }

    /**
     * 在写入锁下重新认证，确认密码仍与已经验证的快照一致，再更新密码、撤销会话和记录审计。
     *
     * @param verifiedUser 旧密码已通过校验的账号快照。
     * @param newHash 新密码的哈希。
     */
    @Transactional
    fun changePassword(verifiedUser: UserRecord, newHash: String) {
        val actor = writes.lockAndAuthenticate()
        val user = users.selectById(actor.user.id) ?: missing("用户")
        if (user.id != verifiedUser.id || user.passwordHash != verifiedUser.passwordHash) invalid("旧密码不正确")
        users.updatePassword(user.id, newHash)
        sessions.revokeForUser(user.id)
        writes.record(actor, "auth.password.change", user.id)
    }

    /** 注销当前会话，只有实际删除会话记录时才写入审计。 */
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
