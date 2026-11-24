package dev.zerokotlin.ktor.system.auth

import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.core.security.digest
import dev.zerokotlin.ktor.change
import dev.zerokotlin.ktor.flag
import dev.zerokotlin.ktor.newId
import dev.zerokotlin.ktor.rows
import dev.zerokotlin.ktor.scopeOrder
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
internal data class CurrentUser(
    val id: String,
    val username: String,
    val displayName: String,
    val departmentId: String?,
    val permissions: Map<String, String>,
    val administrator: Boolean,
)

internal data class Account(
    val id: String,
    val username: String,
    val displayName: String,
    val departmentId: String?,
    val enabled: Boolean,
    val passwordHash: String,
    val email: String?,
    val mobile: String?,
)

internal fun account(row: ResultSet) = Account(
    row.getString("id"), row.getString("username"), row.getString("display_name"),
    row.getString("department_id"), row.getInt("enabled") == 1, row.getString("password_hash"),
    row.getString("email"), row.getString("mobile"),
)

private val tokenPattern = Regex("[A-Za-z0-9_-]{43}")

/** 会话和角色按请求重新读取；业务 Mapper 与这些查询共享外层事务。 */
internal fun Connection.loginAudit(userId: String?, name: String?, success: Boolean, address: String, now: Long) {
    change(
        "INSERT INTO sys_login_audit (id, user_id, username, successful, remote_address, occurred_at) VALUES (?, ?, ?, ?, ?, ?)",
        newId(), userId, name, success.flag(), address.take(128), now,
    )
}

internal fun Connection.requireAccount(raw: String): Account {
    if (!tokenPattern.matches(raw)) throw SystemFailure(401, "会话无效")
    return requireAccountDigest(digest(raw))
}

internal fun Connection.requireAccountDigest(sessionDigest: String): Account {
    return rows(
        """SELECT u.* FROM sys_user u JOIN sys_session s ON s.user_id = u.id
           WHERE s.digest = ? AND s.expires_at > ? AND u.enabled = 1""",
        sessionDigest, System.currentTimeMillis(), map = ::account,
    ).singleOrNull() ?: throw SystemFailure(401, "登录已失效")
}

internal fun Connection.actor(account: Account): CurrentUser {
    val administrator = rows(
        """SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id
           WHERE ur.user_id = ? AND r.id = 'system-admin' AND r.enabled = 1""",
        account.id,
    ) { it.getInt(1) }.isNotEmpty()
    val scopes = if (administrator) rows("SELECT code FROM sys_permission WHERE enabled = 1") { it.getString(1) }
        .associateWith { "ALL" }
    else rows(
        """SELECT p.code, r.data_scope FROM sys_user_role ur
           JOIN sys_role r ON r.id = ur.role_id AND r.enabled = 1
           JOIN sys_role_permission rp ON rp.role_id = r.id
           JOIN sys_permission p ON p.code = rp.permission_code AND p.enabled = 1
           WHERE ur.user_id = ? AND r.id <> 'system-admin'""",
        account.id,
    ) { it.getString(1) to it.getString(2) }.groupBy({ it.first }, { it.second })
        .mapValues { (_, granted) -> granted.maxBy(scopeOrder::indexOf) }
    return CurrentUser(account.id, account.username, account.displayName, account.departmentId, scopes, administrator)
}

internal fun Connection.audit(account: Account, action: String, target: String) {
    change(
        "INSERT INTO sys_operation_audit (id, actor_id, action, target_id, occurred_at) VALUES (?, ?, ?, ?, ?)",
        UUID.randomUUID().toString(), account.id, action, target, System.currentTimeMillis(),
    )
}
