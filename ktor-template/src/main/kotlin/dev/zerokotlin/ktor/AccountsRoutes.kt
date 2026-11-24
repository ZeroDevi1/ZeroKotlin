package dev.zerokotlin.ktor

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.sql.Connection

internal const val userManage = "system:user:manage"
private const val userUpdate = "system:user:update"
private const val positionRead = "system:position:read"
private const val positionWrite = "system:position:write"

@Serializable private data class UserDetail(val user: User, val roleIds: List<String>)
@Serializable private data class CreateUser(
    val username: String, val displayName: String, val password: String, val departmentId: String? = null,
    val roleIds: Set<String> = emptySet(),
)
@Serializable private data class EditUserProfile(val displayName: String)
@Serializable private data class EditUserAccount(
    val departmentId: String? = null, val enabled: Boolean, val roleIds: Set<String>, val password: String? = null,
)
@Serializable private data class ResetUserPassword(val newPassword: String)
@Serializable private data class Position(val id: String, val code: String, val name: String, val enabled: Boolean, val sortOrder: Int)
@Serializable private data class NewPosition(val code: String, val name: String, val sortOrder: Int)
@Serializable private data class EditPosition(val name: String, val enabled: Boolean, val sortOrder: Int)
@Serializable private data class PositionMember(val id: String, val username: String, val displayName: String)
@Serializable private data class PositionMembers(val userIds: Set<String?>)

internal fun Route.accountRoutes(store: SystemStore) = route("users") {
    get {
        val window = call.window()
        val query = call.request.queryParameters
        val department = query["departmentId"]?.takeIf(String::isNotBlank)
        val role = query["roleId"]?.takeIf(String::isNotBlank)
        call.respond(store.read(call.bearer(), userRead) { db, actor -> db.userPage(department, role, window, actor) })
    }
    post {
        val token = call.bearer()
        val request = call.receive<CreateUser>()
        store.read(token, userManage) { _, actor -> requireAdministrator(actor) }
        val name = username(request.username) ?: invalid("用户名格式无效")
        val display = store.plainName(request.displayName)
        val hash = store.accountPassword(request.password)
        val id = newId()
        store.write(token, userManage, administratorOnly = true) { db, _, audit ->
            db.insertUser(id, name, display, hash, request.departmentId, request.roleIds)
            audit("user.create", id)
        }
        call.respond(HttpStatusCode.Created, Created(id))
    }
    post("batch-delete") {
        val token = call.bearer()
        deleteAccounts(store, token, call.receive<DeleteBatch>().ids)
        call.respond(HttpStatusCode.NoContent)
    }
    route("{id}") {
        get {
            val id = call.id()
            call.respond(store.read(call.bearer(), userRead) { db, actor ->
                UserDetail(db.visibleUser(id, actor, userRead), db.userRoleIds(id))
            })
        }
        patch {
            val token = call.bearer()
            val id = call.id()
            val display = store.plainName(call.receive<EditUserProfile>().displayName)
            call.respond(store.write(token, userUpdate) { db, actor, audit ->
                val user = db.visibleUser(id, actor, userUpdate)
                db.change("UPDATE sys_user SET display_name = ? WHERE id = ?", display, id)
                audit("user.profile.update", id)
                user.copy(displayName = display)
            })
        }
        put("account") {
            val token = call.bearer()
            val id = call.id()
            val request = call.receive<EditUserAccount>()
            val hash = request.password?.let {
                store.read(token, userManage) { _, actor -> requireAdministrator(actor) }
                store.accountPassword(it)
            }
            store.write(token, userManage, administratorOnly = true) { db, actor, audit ->
                db.visibleUser(id, actor, userManage)
                val isAdministrator = administratorRole in db.userRoleIds(id)
                if (isAdministrator && (!request.enabled || administratorRole !in request.roleIds)) {
                    invalid("内置管理员必须保持启用并保留系统管理员角色")
                }
                if (!isAdministrator && administratorRole in request.roleIds) invalid("内置管理员角色只能在初始化时授予")
                db.validateDepartment(request.departmentId)
                db.validateRoles(request.roleIds - administratorRole)
                db.change(
                    "UPDATE sys_user SET department_id = ?, enabled = ?, password_hash = COALESCE(?, password_hash) WHERE id = ?",
                    request.departmentId, request.enabled.flag(), hash, id,
                )
                db.setUserRoles(id, request.roleIds)
                if (!request.enabled || hash != null) db.change("DELETE FROM sys_session WHERE user_id = ?", id)
                audit("user.account.update", id)
            }
            call.respond(HttpStatusCode.NoContent)
        }
        put("password") {
            val token = call.bearer()
            val id = call.id()
            val request = call.receive<ResetUserPassword>()
            store.read(token, userManage) { _, actor -> requireAdministrator(actor) }
            val hash = store.accountPassword(request.newPassword)
            store.write(token, userManage, administratorOnly = true) { db, actor, audit ->
                db.visibleUser(id, actor, userManage)
                db.change("UPDATE sys_user SET password_hash = ? WHERE id = ?", hash, id)
                db.change("DELETE FROM sys_session WHERE user_id = ?", id)
                audit("user.password.reset", id)
            }
            call.respond(HttpStatusCode.NoContent)
        }
        delete {
            deleteAccounts(store, call.bearer(), listOf(call.id()))
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

internal fun requireAdministrator(actor: CurrentUser) {
    if (!actor.administrator) throw SystemFailure(403, "仅系统管理员可执行此操作")
}

private fun Connection.visibleUser(id: String, actor: CurrentUser, authority: String): User {
    val visibility = actor.userVisibility(authority)
    return rows(
        """SELECT u.id, u.username, u.display_name, u.department_id, u.enabled
           FROM sys_user u WHERE u.id = ? AND (${visibility.condition})""", id, *visibility.parameters.toTypedArray(),
    ) { User(it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getInt(5) == 1) }
        .singleOrNull() ?: missing("用户")
}

private fun Connection.userRoleIds(id: String) =
    rows("SELECT role_id FROM sys_user_role WHERE user_id = ? ORDER BY role_id", id) { it.getString(1) }

internal fun Connection.setUserRoles(id: String, roles: Set<String>) {
    change("DELETE FROM sys_user_role WHERE user_id = ?", id)
    roles.forEach { change("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", id, it) }
}

internal fun Connection.validateDepartment(id: String?) {
    if (id != null && !exists(this, "SELECT 1 FROM sys_department WHERE id = ?", id)) invalid("部门不存在")
}

internal fun Connection.validateRoles(roles: Set<String>) {
    if (administratorRole in roles) invalid("内置管理员角色只能在初始化时授予")
    if (roles.size > 256) invalid("单个账号最多关联 256 个业务角色")
    if (roles.any { !exists(this, "SELECT 1 FROM sys_role WHERE id = ?", it) }) invalid("角色不存在")
}

private suspend fun deleteAccounts(store: SystemStore, token: String, ids: List<String?>) {
    store.write(token, userManage, administratorOnly = true) { db, actor, audit ->
        if (ids.size !in 1..100) invalid("每次须指定 1 至 100 个待删除 ID")
        val targets = ids.map { it?.takeIf { id -> id.isNotBlank() && id.length <= 36 } ?: invalid("资源 ID 无效") }.toSet()
        targets.forEach { id ->
            db.visibleUser(id, actor, userManage)
            if (administratorRole in db.userRoleIds(id)) invalid("内置管理员账号由系统维护")
        }
        targets.forEach { id ->
            db.change("DELETE FROM sys_session WHERE user_id = ?", id)
            db.change("DELETE FROM sys_user_role WHERE user_id = ?", id)
            db.change("DELETE FROM sys_user_position WHERE user_id = ?", id)
            db.change("DELETE FROM sys_announcement_read WHERE user_id = ?", id)
            db.change("DELETE FROM sys_user WHERE id = ?", id)
            audit("user.delete", id)
        }
    }
}

internal fun Route.positionRoutes(store: SystemStore) = route("positions") {
    get {
        val window = call.window()
        call.respond(store.read(call.bearer(), positionRead) { db, _ ->
            val count = db.rows("SELECT COUNT(*) FROM sys_position") { it.getLong(1) }.single()
            val records = db.rows(
                "SELECT id, code, name, enabled, sort_order FROM sys_position ORDER BY sort_order, id LIMIT ? OFFSET ?",
                window.size, window.offset,
            ) { Position(it.getString(1), it.getString(2), it.getString(3), it.getInt(4) == 1, it.getInt(5)) }
            window.result(records, count)
        })
    }
    post {
        val token = call.bearer()
        val request = call.receive<NewPosition>()
        val id = newId()
        store.write(token, positionWrite, administratorOnly = true) { db, _, audit ->
            val code = request.code.takeIf(permissionPattern::matches) ?: invalid("岗位编码无效")
            if (exists(db, "SELECT 1 FROM sys_position WHERE code = ?", code)) throw SystemFailure(409, "岗位编码已存在")
            db.change(
                "INSERT INTO sys_position (id, code, name, sort_order, enabled) VALUES (?, ?, ?, ?, 1)",
                id, code, checkedName(request.name), request.sortOrder,
            )
            audit("position.create", id)
        }
        call.respond(HttpStatusCode.Created, Created(id))
    }
    route("{id}") {
        put {
            val token = call.bearer()
            val id = call.id()
            val request = call.receive<EditPosition>()
            store.write(token, positionWrite, administratorOnly = true) { db, _, audit ->
                if (db.change(
                        "UPDATE sys_position SET name = ?, enabled = ?, sort_order = ? WHERE id = ?",
                        checkedName(request.name), request.enabled.flag(), request.sortOrder, id,
                    ) == 0
                ) missing("岗位")
                audit("position.update", id)
            }
            call.respond(HttpStatusCode.NoContent)
        }
        get("members") {
            val id = call.id()
            call.respond(store.read(call.bearer(), positionRead) { db, actor ->
                requireAdministrator(actor)
                if (!exists(db, "SELECT 1 FROM sys_position WHERE id = ?", id)) missing("岗位")
                db.rows(
                    """SELECT u.id, u.username, u.display_name FROM sys_user_position p
                       JOIN sys_user u ON u.id = p.user_id WHERE p.position_id = ? ORDER BY u.username, u.id""", id,
                ) { PositionMember(it.getString(1), it.getString(2), it.getString(3)) }
            })
        }
        put("members") {
            val token = call.bearer()
            val id = call.id()
            val request = call.receive<PositionMembers>()
            store.write(token, positionWrite, administratorOnly = true) { db, _, audit ->
                if (!exists(db, "SELECT 1 FROM sys_position WHERE id = ?", id)) missing("岗位")
                val ids = request.userIds.map { it?.takeIf { user -> user.length in 1..36 } ?: invalid("用户 ID 无效") }
                for (batch in ids.chunked(500)) {
                    val marks = batch.joinToString(",") { "?" }
                    val count = db.rows("SELECT COUNT(*) FROM sys_user WHERE id IN ($marks)", *batch.toTypedArray()) {
                        it.getInt(1)
                    }.single()
                    if (count != batch.size) invalid("用户不存在")
                }
                db.change("DELETE FROM sys_user_position WHERE position_id = ?", id)
                ids.forEach { db.change("INSERT INTO sys_user_position (position_id, user_id) VALUES (?, ?)", id, it) }
                audit("position.members.replace", id)
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

internal fun Connection.insertUser(id: String, username: String, displayName: String, hash: String, department: String?, roles: Set<String>) {
    if (exists(this, "SELECT 1 FROM sys_user WHERE username = ?", username)) throw SystemFailure(409, "用户名已存在")
    validateDepartment(department)
    validateRoles(roles)
    change(
        "INSERT INTO sys_user (id, username, display_name, password_hash, department_id, enabled) VALUES (?, ?, ?, ?, ?, 1)",
        id, username, displayName, hash, department,
    )
    setUserRoles(id, roles)
}
