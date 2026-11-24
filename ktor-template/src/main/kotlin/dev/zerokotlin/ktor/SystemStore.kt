package dev.zerokotlin.ktor

import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.flywaydb.core.Flyway
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.sql.ResultSet
import java.util.Base64
import java.util.HexFormat
import java.util.Locale
import java.util.UUID

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
internal data class CurrentUser(
    val id: String,
    val username: String,
    val displayName: String,
    val departmentId: String?,
    val permissions: Map<String, String>,
    val administrator: Boolean,
)

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

@Serializable
internal data class PermissionSummary(val code: String, val name: String, val enabled: Boolean)

@Serializable
internal data class CreatePermission(val code: String, val name: String)

@Serializable
internal data class UpdatePermission(val name: String, val enabled: Boolean)

@Serializable
internal data class InboxEntry(val id: String, val title: String, val body: String, val publishedAt: Long, val readAt: Long?)

@Serializable
internal data class Page<T>(val items: List<T>, val total: Long, val page: Int, val size: Int)

internal class SystemFailure(val status: Int, message: String) : RuntimeException(message)

private val usernamePattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{2,63}")
internal val permissionPattern = Regex("[a-z0-9][a-z0-9:_-]{0,119}")
private val tokenPattern = Regex("[A-Za-z0-9_-]{43}")
private val emailPattern = Regex("[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+")
private val mobilePattern = Regex("\\+?[0-9]{6,15}")
internal val scopeOrder = listOf("SELF", "DEPARTMENT", "DEPARTMENT_AND_DESCENDANTS", "ALL")
private val random = SecureRandom()

internal fun username(value: String) = value.takeIf(usernamePattern::matches)?.lowercase(Locale.ROOT)
private fun acceptablePassword(value: String) = value.length in 1..72 && value.toByteArray().size <= 72
private fun validNewPassword(value: String) = acceptablePassword(value) && value.codePointCount(0, value.length) >= 12
internal fun issueToken() = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
internal fun digest(raw: String) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.US_ASCII)))

internal fun Connection.change(sql: String, vararg args: Any?): Int =
    prepareStatement(sql).use { statement ->
        args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        statement.executeUpdate()
    }

internal fun <T> Connection.rows(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { statement ->
        args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        statement.executeQuery().use { result -> buildList { while (result.next()) add(map(result)) } }
    }

private data class Account(
    val id: String,
    val username: String,
    val displayName: String,
    val departmentId: String?,
    val enabled: Boolean,
    val passwordHash: String,
    val email: String?,
    val mobile: String?,
)

private fun account(row: ResultSet) = Account(
    row.getString("id"), row.getString("username"), row.getString("display_name"),
    row.getString("department_id"), row.getInt("enabled") == 1, row.getString("password_hash"),
    row.getString("email"), row.getString("mobile"),
)

private data class MenuRow(
    val id: String, val parentId: String?, val name: String, val path: String?, val code: String?, val sort: Int, val module: String?,
)

internal class SystemStore(
    settings: DatabaseSettings,
    private val sessionSeconds: Long,
    private val modules: Set<String>,
    private val sanitizeText: Boolean,
) : AutoCloseable {
    private val passwords = BCryptPasswordEncoder(12)
    private val dummyHash = hash(issueToken())
    private val database: HikariDataSource

    init {
        require(sessionSeconds in 1..604800) { "会话有效期须为 1 至 604800 秒" }
        val (vendor, source) = settings.connect()
        database = source
        try {
            val core = Flyway.configure().dataSource(source)
                .locations("classpath:db/migration/common", "classpath:db/migration/$vendor")
                // 保留原 V9 的校验和；Ktor 始终允许 SQL 在空用户表中创建默认账号。
                .placeholders(mapOf("zeroSkipDefaultAdmin" to "false")).load()
            core.migrate()
            // 每个业务模块独立记录版本，允许它们各自从 V1 开始。
            for (module in modules.sorted()) {
                Flyway.configure(core.configuration.classLoader).configuration(core.configuration)
                    .locations("classpath:db/modules/$module").table("zero_history_$module")
                    .baselineOnMigrate(true).baselineVersion("0").load().migrate()
            }
        } catch (failure: Exception) {
            source.close()
            throw failure
        }
    }

    fun plainName(value: String): String {
        val text = checkedName(value)
        return if (sanitizeText && ('<' in text || '&' in text)) {
            checkedName(org.jsoup.safety.Cleaner(org.jsoup.safety.Safelist.none())
                .clean(org.jsoup.Jsoup.parseBodyFragment(text)).body().wholeText())
        } else text
    }

    suspend fun accountPassword(password: String): String = withContext(Dispatchers.IO) {
        if (!validNewPassword(password)) throw SystemFailure(400, "密码至少 12 个字符且最多 72 个 UTF-8 字节")
        hash(password)
    }

    private fun hash(password: String) = checkNotNull(passwords.encode(password))

    internal fun <T> transaction(write: Boolean = false, action: (Connection) -> T): T = database.connection.use { db ->
        db.autoCommit = false
        try {
            // 复用系统写锁，先加锁再复核会话和密码快照。
            if (write) check(db.change("UPDATE sys_write_guard SET revision = revision + 1 WHERE id = 'system'") == 1)
            action(db).also { db.commit() }
        } catch (failure: Throwable) {
            try { db.rollback() } catch (rollback: Exception) { failure.addSuppressed(rollback) }
            throw failure
        }
    }

    suspend fun login(request: LoginRequest, remoteAddress: String, captcha: LoginCaptchaService): LoginResponse? = withContext(Dispatchers.IO) {
        val normalized = username(request.username)
        if (!captcha.verify(request.captchaId, request.captchaCode)) {
            transaction(write = true) { db -> db.loginAudit(null, normalized, false, remoteAddress, System.currentTimeMillis()) }
            throw SystemFailure(400, "验证码错误或已失效，请刷新后重试。")
        }
        val verified = transaction { db ->
            normalized?.let { db.rows("SELECT * FROM sys_user WHERE username = ?", it, map = ::account).singleOrNull() }
        }
        val matches = acceptablePassword(request.password) &&
            passwords.matches(request.password, verified?.passwordHash ?: dummyHash)
        transaction(write = true) { db ->
            val current = if (matches && verified?.enabled == true)
                db.rows("SELECT * FROM sys_user WHERE id = ? AND enabled = 1", verified.id, map = ::account)
                    .singleOrNull()?.takeIf { it.passwordHash == verified.passwordHash }
            else null
            val now = System.currentTimeMillis()
            db.loginAudit(verified?.id, normalized, current != null, remoteAddress, now)
            current?.let {
                val raw = issueToken()
                val expires = now + sessionSeconds * 1000
                db.change("DELETE FROM sys_session WHERE expires_at <= ?", now)
                db.change("INSERT INTO sys_session (digest, user_id, expires_at) VALUES (?, ?, ?)", digest(raw), it.id, expires)
                LoginResponse(accessToken = raw, expiresAt = expires)
            }
        }
    }

    private fun Connection.loginAudit(userId: String?, name: String?, success: Boolean, address: String, now: Long) {
        change(
            "INSERT INTO sys_login_audit (id, user_id, username, successful, remote_address, occurred_at) VALUES (?, ?, ?, ?, ?, ?)",
            newId(), userId, name, success.flag(), address.take(128), now,
        )
    }

    private fun Connection.requireAccount(raw: String): Account {
        if (!tokenPattern.matches(raw)) throw SystemFailure(401, "会话无效")
        return requireAccountDigest(digest(raw))
    }

    private fun Connection.requireAccountDigest(sessionDigest: String): Account {
        return rows(
            """SELECT u.* FROM sys_user u JOIN sys_session s ON s.user_id = u.id
               WHERE s.digest = ? AND s.expires_at > ? AND u.enabled = 1""",
            sessionDigest, System.currentTimeMillis(), map = ::account,
        ).singleOrNull() ?: throw SystemFailure(401, "登录已失效")
    }

    private fun Connection.actor(account: Account): CurrentUser {
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

    private fun Connection.audit(account: Account, action: String, target: String) {
        change(
            "INSERT INTO sys_operation_audit (id, actor_id, action, target_id, occurred_at) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID().toString(), account.id, action, target, System.currentTimeMillis(),
        )
    }

    internal suspend fun <T> read(
        raw: String,
        authority: String?,
        action: (Connection, CurrentUser) -> T,
    ): T = withContext(Dispatchers.IO) {
        transaction { db ->
            val actor = db.actor(db.requireAccount(raw))
            if (authority != null && authority !in actor.permissions) throw SystemFailure(403, "没有权限")
            action(db, actor)
        }
    }

    /** 在同一写事务内复核会话和权限，并随业务变更提交审计。 */
    internal suspend fun <T> write(
        raw: String,
        authority: String,
        administratorOnly: Boolean = false,
        action: (Connection, CurrentUser, (String, String) -> Unit) -> T,
    ): T = withContext(Dispatchers.IO) {
        transaction(write = true) { db ->
            val account = db.requireAccount(raw)
            val actor = db.actor(account)
            if (authority !in actor.permissions || (administratorOnly && !actor.administrator)) {
                throw SystemFailure(403, "没有权限")
            }
            action(db, actor) { name, target -> db.audit(account, name, target) }
        }
    }

    suspend fun monitorAudit(actorId: String?, status: Int, tookMs: Long, exception: String?, address: String) =
        withContext(Dispatchers.IO) {
            transaction { db ->
                db.change(
                    """INSERT INTO sys_request_audit
                       (id, actor_id, action, http_method, route_pattern, http_status, duration_ms, exception_type, remote_address, occurred_at)
                       VALUES (?, ?, 'monitor.read', 'GET', '/api/system/monitor', ?, ?, ?, ?, ?)""",
                    newId(), actorId, status, tookMs, exception, address.take(128), System.currentTimeMillis(),
                )
            }
        }

    suspend fun currentDigest(sessionDigest: String) = withContext(Dispatchers.IO) {
        transaction { db -> db.actor(db.requireAccountDigest(sessionDigest)) }
    }

    suspend fun current(raw: String) = withContext(Dispatchers.IO) {
        transaction { db -> db.actor(db.requireAccount(raw)) }
    }

    suspend fun menus(raw: String) = withContext(Dispatchers.IO) {
        transaction { db ->
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
            }.filter { (it.module == null || it.module in modules) && (it.code == null || it.code in actor.permissions) }
                .groupBy { it.parentId }
            fun tree(parent: String?): List<MenuNode> = children[parent].orEmpty().mapNotNull { row ->
                val nodes = tree(row.id)
                // 未迁移页面不下发链接；保留有可见后代的祖先层级。
                val path = row.path?.takeIf { (it in implementedMenuPaths || row.module in modules) && (selected == null || row.id in selected) }
                if (path == null && nodes.isEmpty()) null
                else MenuNode(row.id, row.name, path, row.code, row.sort, true, nodes)
            }
            tree(null)
        }
    }

    suspend fun logout(raw: String): Unit = withContext(Dispatchers.IO) {
        transaction<Unit>(write = true) { db ->
            val account = db.requireAccount(raw)
            if (db.change("DELETE FROM sys_session WHERE digest = ?", digest(raw)) == 1) db.audit(account, "auth.logout", account.id)
        }
    }

    suspend fun profile(raw: String) = withContext(Dispatchers.IO) {
        transaction { db -> db.requireAccount(raw).let { OwnProfile(it.username, it.displayName, it.email, it.mobile) } }
    }

    suspend fun updateProfile(raw: String, request: ProfileRequest) = withContext(Dispatchers.IO) {
        transaction(write = true) { db ->
            val account = db.requireAccount(raw)
            val name = plainName(request.displayName)
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
        val verified = transaction { it.requireAccount(raw) }
        if (!validNewPassword(request.newPassword)) throw SystemFailure(400, "密码至少 12 个字符且最多 72 个 UTF-8 字节")
        if (!acceptablePassword(request.oldPassword) || !passwords.matches(request.oldPassword, verified.passwordHash)) {
            throw SystemFailure(400, "旧密码不正确")
        }
        val hashed = hash(request.newPassword)
        transaction(write = true) { db ->
            val account = db.requireAccount(raw)
            if (account.passwordHash != verified.passwordHash) throw SystemFailure(400, "旧密码不正确")
            db.change("UPDATE sys_user SET password_hash = ? WHERE id = ?", hashed, account.id)
            db.change("DELETE FROM sys_session WHERE user_id = ?", account.id)
            db.audit(account, "auth.password.change", account.id)
        }
    }

    suspend fun permissions(raw: String) = withContext(Dispatchers.IO) {
        transaction { db ->
            if ("system:permission:read" !in db.actor(db.requireAccount(raw)).permissions) throw SystemFailure(403, "没有权限")
            db.rows("SELECT code, name, enabled FROM sys_permission ORDER BY code") {
                PermissionSummary(it.getString(1), it.getString(2), it.getInt(3) == 1)
            }
        }
    }

    suspend fun savePermission(raw: String, code: String, name: String, enabled: Boolean, create: Boolean) = withContext(Dispatchers.IO) {
        transaction(write = true) { db ->
            val account = db.requireAccount(raw)
            val actor = db.actor(account)
            if (!actor.administrator || "system:permission:write" !in actor.permissions) throw SystemFailure(403, "没有权限")
            if (!permissionPattern.matches(code) || code.startsWith("system:")) throw SystemFailure(400, "权限编码无效或由系统维护")
            val label = checkedName(name)
            if (create) {
                if (db.rows("SELECT 1 FROM sys_permission WHERE code = ?", code) { it.getInt(1) }.isNotEmpty()) {
                    throw SystemFailure(409, "权限编码已存在")
                }
                db.change("INSERT INTO sys_permission (code, name, enabled) VALUES (?, ?, 1)", code, label)
            } else if (db.change("UPDATE sys_permission SET name = ?, enabled = ? WHERE code = ?", label, if (enabled) 1 else 0, code) == 0) {
                throw SystemFailure(404, "权限点不存在")
            }
            db.audit(account, if (create) "permission.create" else "permission.update", code)
            PermissionSummary(code, label, enabled)
        }
    }

    suspend fun inbox(raw: String, page: Int, size: Int, unread: Boolean) = withContext(Dispatchers.IO) {
        transaction { db ->
            val account = db.requireAccount(raw)
            val filter = """FROM sys_announcement a LEFT JOIN sys_announcement_read r
                ON r.announcement_id = a.id AND r.user_id = ?
                WHERE a.status = 'PUBLISHED'""" + if (unread) " AND r.user_id IS NULL" else ""
            val items = db.rows(
                "SELECT a.id, a.title, a.body, a.published_at, r.read_at $filter ORDER BY a.published_at DESC, a.id DESC LIMIT ? OFFSET ?",
                account.id, size, (page.toLong() - 1) * size,
            ) { row -> InboxEntry(row.getString(1), row.getString(2), row.getString(3), row.getLong(4),
                row.getLong(5).takeUnless { row.wasNull() }) }
            Page(items, db.rows("SELECT COUNT(*) $filter", account.id) { it.getLong(1) }.single(), page, size)
        }
    }

    suspend fun markRead(raw: String, id: String?): Unit = withContext(Dispatchers.IO) {
        transaction<Unit>(write = true) { db ->
            val account = db.requireAccount(raw)
            if (id != null && db.rows("SELECT 1 FROM sys_announcement WHERE id = ? AND status = 'PUBLISHED'", id) { it.getInt(1) }.isEmpty()) {
                throw SystemFailure(404, "公告不存在")
            }
            val byId = if (id == null) "" else " AND a.id = ?"
            val args = listOfNotNull(account.id, System.currentTimeMillis(), account.id, id).toTypedArray()
            db.change(
                """INSERT INTO sys_announcement_read (user_id, announcement_id, read_at)
                   SELECT ?, a.id, ? FROM sys_announcement a
                   WHERE a.status = 'PUBLISHED' AND NOT EXISTS (
                       SELECT 1 FROM sys_announcement_read r WHERE r.user_id = ? AND r.announcement_id = a.id
                   )$byId""",
                *args,
            )
        }
    }

    suspend fun discardFileUpload(id: String, removeObject: suspend () -> Unit) = withContext(Dispatchers.IO) {
        val owned = transaction(write = true) { db ->
            db.change("UPDATE sys_file SET state = 'DELETING' WHERE id = ? AND state = 'UPLOADING'", id) == 1
        }
        if (owned) {
            removeObject()
            transaction(write = true) { db ->
                db.change("DELETE FROM sys_file WHERE id = ? AND state = 'DELETING'", id)
            }
        }
    }

    suspend fun healthy(): Boolean = withContext(Dispatchers.IO) {
        try { transaction { db -> db.rows("SELECT 1") { it.getInt(1) }.singleOrNull() == 1 } }
        catch (_: java.sql.SQLException) { false }
    }

    override fun close() = database.close()
}

internal fun checkedName(value: String): String =
    value.trim().takeIf { it.isNotEmpty() && it.length <= 120 && it.none { ch -> Character.isISOControl(ch) } }
        ?: throw SystemFailure(400, "名称须为 1 至 120 个字符")
