package dev.zerokotlin.ktor.system.auth

import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.core.security.LoginCaptchaService
import dev.zerokotlin.core.security.digest
import dev.zerokotlin.core.security.issueToken
import dev.zerokotlin.ktor.SystemStore
import dev.zerokotlin.ktor.change
import dev.zerokotlin.ktor.implementedMenuPaths
import dev.zerokotlin.ktor.rows
import dev.zerokotlin.ktor.system.account.matchesLoginPassword
import dev.zerokotlin.ktor.system.account.newPasswordHash
import dev.zerokotlin.ktor.system.account.username
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
internal data class LoginRequest(
    val username: String,
    val password: String,
    val captchaId: String? = null,
    val captchaCode: String? = null,
)

@Serializable
internal data class LoginResponse(val accessToken: String, val tokenType: String = "Bearer", val expiresAt: Long)

@Serializable
internal data class MenuNode(
    val id: String,
    val name: String,
    val path: String?,
    val permissionCode: String?,
    val sortOrder: Int,
    val enabled: Boolean,
    val children: List<MenuNode>,
)

@Serializable
internal data class OwnProfile(val username: String, val displayName: String, val email: String?, val mobile: String?)

@Serializable
internal data class ProfileRequest(val displayName: String, val email: String? = null, val mobile: String? = null)

@Serializable
internal data class PasswordRequest(val oldPassword: String, val newPassword: String)

internal data class MenuRow(
    val id: String, val parentId: String?, val name: String, val path: String?, val code: String?, val sort: Int, val module: String?,
)

private val emailPattern = Regex("[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+")
private val mobilePattern = Regex("\\+?[0-9]{6,15}")

/** 登录、个人资料和导航业务。密码哈希计算在获取系统写锁之前执行。 */
internal class AuthService(private val store: SystemStore, private val captcha: LoginCaptchaService) {
    suspend fun captcha() = captcha.issue()

    suspend fun current(raw: String): CurrentUser = store.current(raw)

    suspend fun login(request: LoginRequest, remoteAddress: String): LoginResponse? = withContext(Dispatchers.IO) {
        val normalized = username(request.username)
        if (!captcha.verify(request.captchaId, request.captchaCode)) {
            store.transaction(write = true) { db -> db.loginAudit(null, normalized, false, remoteAddress, System.currentTimeMillis()) }
            throw SystemFailure(400, "验证码错误或已失效，请刷新后重试。")
        }
        val verified = store.transaction { db ->
            normalized?.let { db.rows("SELECT * FROM sys_user WHERE username = ?", it, map = ::account).singleOrNull() }
        }
        val matches = matchesLoginPassword(request.password, verified?.passwordHash)
        store.transaction(write = true) { db ->
            val current = if (matches && verified?.enabled == true)
                db.rows("SELECT * FROM sys_user WHERE id = ? AND enabled = 1", verified.id, map = ::account)
                    .singleOrNull()?.takeIf { it.passwordHash == verified.passwordHash }
            else null
            val now = System.currentTimeMillis()
            db.loginAudit(verified?.id, normalized, current != null, remoteAddress, now)
            current?.let {
                val raw = issueToken()
                val expires = now + store.sessionSeconds * 1000
                db.change("DELETE FROM sys_session WHERE expires_at <= ?", now)
                db.change("INSERT INTO sys_session (digest, user_id, expires_at) VALUES (?, ?, ?)", digest(raw), it.id, expires)
                LoginResponse(accessToken = raw, expiresAt = expires)
            }
        }
    }

    suspend fun menus(raw: String) = withContext(Dispatchers.IO) {
        store.transaction { db ->
            val actor = db.actor(db.requireAccount(raw))
            val direct = if (actor.administrator) emptyList() else db.rows(
                """SELECT rm.menu_id FROM sys_user_role ur
                   JOIN sys_role r ON r.id = ur.role_id AND r.enabled = 1 AND r.direct_menus = 1
                   LEFT JOIN sys_role_menu rm ON rm.role_id = r.id WHERE ur.user_id = ?""",
                actor.id,
            ) { it.getString(1) }
            val selected = direct.takeIf { it.isNotEmpty() }?.filterNotNull()?.toSet()
            val children = db.rows(
                """SELECT id, parent_id, name, path, permission_code, sort_order, module_name FROM sys_menu
                   WHERE enabled = 1 ORDER BY sort_order, id""",
            ) {
                MenuRow(it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getString(5), it.getInt(6), it.getString(7))
            }.filter { (it.module == null || it.module in store.modules) && (it.code == null || it.code in actor.permissions) }
                .groupBy { it.parentId }
            fun tree(parent: String?): List<MenuNode> = children[parent].orEmpty().mapNotNull { row ->
                val nodes = tree(row.id)
                // 未迁移页面不下发链接；保留有可见后代的祖先层级。
                val path = row.path?.takeIf { (it in implementedMenuPaths || row.module in store.modules) && (selected == null || row.id in selected) }
                if (path == null && nodes.isEmpty()) null
                else MenuNode(row.id, row.name, path, row.code, row.sort, true, nodes)
            }
            tree(null)
        }
    }

    suspend fun logout(raw: String): Unit = withContext(Dispatchers.IO) {
        store.transaction<Unit>(write = true) { db ->
            val account = db.requireAccount(raw)
            if (db.change("DELETE FROM sys_session WHERE digest = ?", digest(raw)) == 1) db.audit(account, "auth.logout", account.id)
        }
    }

    suspend fun profile(raw: String) = withContext(Dispatchers.IO) {
        store.transaction { db -> db.requireAccount(raw).let { OwnProfile(it.username, it.displayName, it.email, it.mobile) } }
    }

    suspend fun updateProfile(raw: String, request: ProfileRequest) = withContext(Dispatchers.IO) {
        store.transaction(write = true) { db ->
            val account = db.requireAccount(raw)
            val name = store.plainName(request.displayName)
            val email = request.email?.trim()?.ifEmpty { null }
            val mobile = request.mobile?.trim()?.ifEmpty { null }
            if (email != null && (email.length > 254 || email.any { Character.isISOControl(it) } || !emailPattern.matches(email))) {
                throw SystemFailure(400, "邮箱格式无效")
            }
            if (mobile != null && !mobilePattern.matches(mobile)) throw SystemFailure(400, "手机号格式无效")
            db.change("UPDATE sys_user SET display_name = ?, email = ?, mobile = ? WHERE id = ?", name, email, mobile, account.id)
            db.audit(account, "auth.profile.update", account.id)
            OwnProfile(account.username, name, email, mobile)
        }
    }

    suspend fun changePassword(raw: String, request: PasswordRequest) = withContext(Dispatchers.IO) {
        val verified = store.transaction { it.requireAccount(raw) }
        val hashed = newPasswordHash(request.oldPassword, request.newPassword, verified.passwordHash)
        store.transaction(write = true) { db ->
            val account = db.requireAccount(raw)
            if (account.passwordHash != verified.passwordHash) throw SystemFailure(400, "旧密码不正确")
            db.change("UPDATE sys_user SET password_hash = ? WHERE id = ?", hashed, account.id)
            db.change("DELETE FROM sys_session WHERE user_id = ?", account.id)
            db.audit(account, "auth.password.change", account.id)
        }
    }
}
