package dev.zerokotlin.system

import dev.zerokotlin.core.security.BearerTokens
import dev.zerokotlin.core.security.hashPassword
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

class LoginRequest(val username: String, val password: String)
data class LoginResponse(val accessToken: String, val tokenType: String, val expiresAt: Long)
data class CurrentUserResponse(
    val id: String,
    val username: String,
    val displayName: String,
    val departmentId: String?,
    val permissions: Map<String, DataScope>,
)

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

@Component
class SystemAdministratorBootstrap(
    private val users: UserMapper,
    private val relations: SystemRelationMapper,
    private val writes: SystemWrites,
    private val passwords: PasswordEncoder,
    @param:Value("\${zero.system.admin.username:}") private val username: String,
    @param:Value("\${zero.system.admin.password:}") private val password: String,
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        if (username.isEmpty() && password.isEmpty()) return
        require(username.isNotEmpty() && password.isNotEmpty()) {
            "首次初始化必须同时设置 ZERO_SYSTEM_ADMIN_USERNAME 和 ZERO_SYSTEM_ADMIN_PASSWORD"
        }
        writes.lock()
        if (users.selectCount(null) != 0L) return

        val normalized = requireNotNull(normalizeUsername(username)) { "首次管理员用户名格式无效" }
        require(acceptableNewPassword(password)) { "首次管理员密码长度无效" }

        val id = newId()
        users.insert(UserRecord(id, normalized, "系统管理员", passwords.hashPassword(password)))
        relations.insertUserRole(id, ADMIN_ROLE_ID)
    }
}

@RestController
@RequestMapping("/api/auth")
class SystemAuthenticationController(
    private val authentication: SystemAuthentication,
    private val sessions: SystemSessionTransactions,
    private val menus: SystemMenus,
) {
    @PostMapping("/login")
    fun login(@RequestBody credentials: LoginRequest, request: HttpServletRequest): ResponseEntity<LoginResponse> {
        val result = authentication.login(credentials, request.remoteAddr.take(128))
        return if (result == null) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore()).build()
        } else {
            ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result)
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout() = sessions.logout()

    @GetMapping("/me")
    fun me(): CurrentUserResponse {
        val actor = currentSystemActor()
        return CurrentUserResponse(
            actor.user.id, actor.user.username, actor.user.displayName, actor.user.departmentId, actor.scopes,
        )
    }

    @GetMapping("/me/menus")
    fun menus(): List<MenuNode> = menus.visibleTree(currentSystemActor())
}
