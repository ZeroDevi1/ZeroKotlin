package dev.zerokotlin.ktor

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

internal val implementedMenuPaths = setOf("/permissions", "/roles", "/menus", "/departments", "/users", "/positions", "/dictionaries", "/announcements", "/audits", "/operations", "/files", "/jobs", "/realtime")

internal const val administratorRole = "system-admin"
private const val roleRead = "system:role:read"
private const val roleWrite = "system:role:write"
private const val menuRead = "system:menu:read"
private const val menuWrite = "system:menu:write"
private const val departmentRead = "system:department:read"
private const val departmentWrite = "system:department:write"
internal const val userRead = "system:user:read"
private val administratorPermissions = setOf(
    "system:user:manage", roleWrite, "system:permission:write", departmentWrite, "system:position:write",
)

@Serializable internal data class Created(val id: String)
@Serializable internal data class DeleteBatch(val ids: List<String?>)
@Serializable private data class Role(val id: String, val code: String, val name: String, val dataScope: String, val enabled: Boolean)
@Serializable private data class RoleDetail(val role: Role, val permissionCodes: List<String>)
@Serializable private data class CreateRole(
    val code: String, val name: String, val dataScope: String, val permissionCodes: Set<String> = emptySet(),
)
@Serializable private data class UpdateRole(val name: String, val dataScope: String, val enabled: Boolean, val permissionCodes: Set<String>)
@Serializable private enum class MenuMode { LEGACY, DIRECT }
@Serializable private data class RoleMenus(val mode: MenuMode, val menuIds: Set<String>)
@Serializable private data class MenuRequest(
    val name: String, val parentId: String? = null, val path: String? = null, val permissionCode: String? = null,
    val sortOrder: Int = 0, val enabled: Boolean = true,
)
@Serializable private data class DepartmentRequest(val name: String, val parentId: String? = null, val sortOrder: Int = 0)
@Serializable private data class DepartmentNode(val id: String, val name: String, val sortOrder: Int, val children: List<DepartmentNode>)
@Serializable internal data class User(
    val id: String, val username: String, val displayName: String, val departmentId: String?, val enabled: Boolean,
)
@Serializable internal data class AssignedRole(val id: String, val code: String, val name: String)
@Serializable internal data class UserEntry(val user: User, val roles: List<AssignedRole>)

private data class HierarchyRow(val id: String, val parent: String?, val name: String, val order: Int)
private data class MenuRecord(val hierarchy: HierarchyRow, val path: String?, val permission: String?, val enabled: Boolean)
internal data class Window(val page: Int, val size: Int) {
    val offset = (page.toLong() - 1) * size
    fun <T> result(items: List<T>, total: Long) = Page(items, total, page, size)
}

internal fun ApplicationCall.window(): Window {
    val query = request.queryParameters
    val page = query["page"]?.let { it.toIntOrNull() ?: 0 } ?: 1
    val size = query["size"]?.let { it.toIntOrNull() ?: 0 } ?: 20
    if (page < 1 || size !in 1..100) throw SystemFailure(400, "page 必须大于等于 1，size 必须介于 1 和 100 之间")
    return Window(page, size)
}

internal fun ApplicationCall.id() = parameters["id"] ?: throw SystemFailure(400, "缺少资源 ID")
internal fun newId() = UUID.randomUUID().toString()
internal fun Boolean.flag() = if (this) 1 else 0
internal fun invalid(message: String): Nothing = throw SystemFailure(400, message)
internal fun missing(name: String): Nothing = throw SystemFailure(404, "${name}不存在")
internal fun exists(db: Connection, sql: String, vararg values: Any?) = db.rows(sql, *values) { it.getInt(1) }.isNotEmpty()

internal fun Route.administrationRoutes(store: SystemStore, files: FileStorages, security: SecurityState) {
    route("system") {
        roleRoutes(store)
        menuRoutes(store)
        departmentRoutes(store)
        accountRoutes(store)
        positionRoutes(store)
        dictionaryRoutes(store)
        announcementRoutes(store)
        auditRoutes(store)
        monitorRoutes(store, security)
        fileRoutes(store, files)
        excelRoutes(store)
        jobRoutes(store)
    }
}

private fun Route.roleRoutes(store: SystemStore) = route("roles") {
    get {
        val window = call.window()
        val enabled = call.request.queryParameters["enabled"]?.let { it.toBooleanStrictOrNull() ?: invalid("enabled 必须为布尔值") }
        val args = enabled?.let { arrayOf(it.flag()) } ?: emptyArray()
        val condition = if (enabled == null) "" else " WHERE enabled = ?"
        call.respond(store.read(call.bearer(), roleRead) { db, _ ->
            val count = db.rows("SELECT COUNT(*) FROM sys_role$condition", *args) { it.getLong(1) }.single()
            val roles = db.rows(
                "SELECT * FROM sys_role$condition ORDER BY code, id LIMIT ? OFFSET ?",
                *args, window.size, window.offset, map = ::role,
            )
            window.result(roles, count)
        })
    }
    post {
        val token = call.bearer()
        val request = call.receive<CreateRole>()
        call.respond(HttpStatusCode.Created, store.write(token, roleWrite, administratorOnly = true) { db, _, audit ->
            val code = request.code.takeIf(permissionPattern::matches) ?: invalid("角色编码无效")
            if (exists(db, "SELECT 1 FROM sys_role WHERE code = ?", code)) throw SystemFailure(409, "角色编码已存在")
            val id = newId()
            db.change(
                "INSERT INTO sys_role (id, code, name, data_scope) VALUES (?, ?, ?, ?)",
                id, code, checkedName(request.name), checkedScope(request.dataScope),
            )
            db.replacePermissions(id, request.permissionCodes)
            audit("role.create", id)
            Created(id)
        })
    }
    post("batch-delete") {
        val token = call.bearer()
        val ids = call.receive<DeleteBatch>().ids
        deleteRoles(store, token, ids)
        call.respond(HttpStatusCode.NoContent)
    }
    route("{id}") {
        get {
            val id = call.id()
            call.respond(store.read(call.bearer(), roleRead) { db, _ ->
                val role = db.requireRole(id)
                val codes = if (id == administratorRole) db.rows("SELECT code FROM sys_permission WHERE enabled = 1 ORDER BY code") {
                    it.getString(1)
                } else db.rows("SELECT permission_code FROM sys_role_permission WHERE role_id = ? ORDER BY permission_code", id) {
                    it.getString(1)
                }
                RoleDetail(role, codes)
            })
        }
        put {
            val token = call.bearer()
            val id = call.id()
            val request = call.receive<UpdateRole>()
            store.write(token, roleWrite, administratorOnly = true) { db, _, audit ->
                db.requireBusinessRole(id)
                db.change(
                    "UPDATE sys_role SET name = ?, data_scope = ?, enabled = ? WHERE id = ?",
                    checkedName(request.name), checkedScope(request.dataScope), request.enabled.flag(), id,
                )
                db.replacePermissions(id, request.permissionCodes)
                audit("role.update", id)
            }
            call.respond(HttpStatusCode.NoContent)
        }
        delete {
            deleteRoles(store, call.bearer(), listOf(call.id()))
            call.respond(HttpStatusCode.NoContent)
        }
        get("menus") {
            val id = call.id()
            call.respond(store.read(call.bearer(), roleRead) { db, _ ->
                db.requireBusinessRole(id)
                val mode = db.rows("SELECT direct_menus FROM sys_role WHERE id = ?", id) {
                    if (it.getInt(1) == 1) MenuMode.DIRECT else MenuMode.LEGACY
                }.single()
                val ids = db.rows("SELECT menu_id FROM sys_role_menu WHERE role_id = ? ORDER BY menu_id", id) { it.getString(1) }
                RoleMenus(mode, ids.toSet())
            })
        }
        put("menus") {
            val token = call.bearer()
            val id = call.id()
            val request = call.receive<RoleMenus>()
            store.write(token, roleWrite, administratorOnly = true) { db, _, audit ->
                db.requireBusinessRole(id)
                if (request.mode == MenuMode.LEGACY && request.menuIds.isNotEmpty()) invalid("沿用权限点规则时须清空直接菜单")
                if (request.menuIds.size > 256) invalid("单个角色最多直接分配 256 个菜单")
                if (request.menuIds.any { !exists(db, "SELECT 1 FROM sys_menu WHERE id = ?", it) }) invalid("菜单不存在")
                db.change("UPDATE sys_role SET direct_menus = ? WHERE id = ?", (request.mode == MenuMode.DIRECT).flag(), id)
                db.change("DELETE FROM sys_role_menu WHERE role_id = ?", id)
                request.menuIds.forEach { db.change("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)", id, it) }
                audit("role.menus.update", id)
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun role(row: ResultSet) = Role(
    row.getString("id"), row.getString("code"), row.getString("name"), row.getString("data_scope"), row.getInt("enabled") == 1,
)

private fun Connection.requireRole(id: String): Role =
    rows("SELECT * FROM sys_role WHERE id = ?", id, map = ::role).singleOrNull() ?: missing("角色")

private fun Connection.requireBusinessRole(id: String): Role =
    requireRole(id).also { if (id == administratorRole) invalid("内置管理员角色由系统维护") }

private fun checkedScope(value: String) = value.takeIf { it in scopeOrder } ?: invalid("角色数据范围无效")

private fun Connection.replacePermissions(id: String, codes: Set<String>) {
    if (codes.size > 256) invalid("单个角色最多关联 256 个权限点")
    if (codes.any { it in administratorPermissions }) invalid("所选权限由内置管理员专用")
    if (codes.any { !permissionPattern.matches(it) || !exists(this, "SELECT 1 FROM sys_permission WHERE code = ?", it) }) {
        invalid("权限点不存在或编码无效")
    }
    change("DELETE FROM sys_role_permission WHERE role_id = ?", id)
    codes.forEach { change("INSERT INTO sys_role_permission (role_id, permission_code) VALUES (?, ?)", id, it) }
}

private suspend fun deleteRoles(store: SystemStore, token: String, ids: List<String?>) {
    store.write(token, roleWrite, administratorOnly = true) { db, _, audit ->
        if (ids.size !in 1..100) invalid("每次须指定 1 至 100 个待删除 ID")
        val targets = ids.map { it?.takeIf { id -> id.isNotBlank() && id.length <= 36 } ?: invalid("资源 ID 无效") }.toSet()
        targets.forEach { id ->
            val role = db.requireBusinessRole(id)
            if (exists(db, "SELECT 1 FROM sys_user_role WHERE role_id = ?", id)) {
                throw SystemFailure(409, "角色 ${role.code} 仍分配给用户")
            }
        }
        targets.forEach { id ->
            db.change("DELETE FROM sys_role_permission WHERE role_id = ?", id)
            db.change("DELETE FROM sys_role_menu WHERE role_id = ?", id)
            db.change("DELETE FROM sys_role WHERE id = ?", id)
            audit("role.delete", id)
        }
    }
}

private fun Route.menuRoutes(store: SystemStore) = route("menus") {
    get {
        call.respond(store.read(call.bearer(), menuRead) { db, _ ->
            val entries = db.rows("SELECT * FROM sys_menu ORDER BY sort_order, id") {
                MenuRecord(hierarchy(it), it.getString("path"), it.getString("permission_code"), it.getInt("enabled") == 1)
            }.groupBy { it.hierarchy.parent }
            fun tree(parent: String?): List<MenuNode> = entries[parent].orEmpty().map { row ->
                val h = row.hierarchy
                MenuNode(h.id, h.name, row.path, row.permission, h.order, row.enabled, tree(h.id))
            }
            tree(null)
        })
    }
    post {
        val token = call.bearer()
        val request = call.receive<MenuRequest>()
        val id = newId()
        saveMenu(store, token, id, request, create = true)
        call.respond(HttpStatusCode.Created, Created(id))
    }
    put("{id}") {
        val token = call.bearer()
        val id = call.id()
        saveMenu(store, token, id, call.receive<MenuRequest>(), create = false)
        call.respond(HttpStatusCode.NoContent)
    }
}

private suspend fun saveMenu(store: SystemStore, token: String, id: String, request: MenuRequest, create: Boolean) {
    store.write(token, menuWrite) { db, _, audit ->
        val parentById = db.rows("SELECT id, parent_id FROM sys_menu") { it.getString(1) to it.getString(2) }.toMap()
        if (!create && id !in parentById) missing("菜单")
        validateParent(id, request.parentId, parentById)
        val code = request.permissionCode
        if (code != null && (!permissionPattern.matches(code) || !exists(db, "SELECT 1 FROM sys_permission WHERE code = ?", code))) {
            invalid("权限点不存在或编码无效")
        }
        val path = request.path?.trim()
        if (path != null && (!path.startsWith("/") || path.startsWith("//") || path.length > 255 ||
                path.any { it == '\\' || Character.isISOControl(it) })) invalid("菜单路径须为站内路径")
        val name = checkedName(request.name)
        if (create) db.change(
            "INSERT INTO sys_menu (id, parent_id, name, path, permission_code, sort_order, enabled) VALUES (?, ?, ?, ?, ?, ?, ?)",
            id, request.parentId, name, path, code, request.sortOrder, request.enabled.flag(),
        ) else db.change(
            "UPDATE sys_menu SET parent_id = ?, name = ?, path = ?, permission_code = ?, sort_order = ?, enabled = ? WHERE id = ?",
            request.parentId, name, path, code, request.sortOrder, request.enabled.flag(), id,
        )
        audit(if (create) "menu.create" else "menu.update", id)
    }
}

private fun Route.departmentRoutes(store: SystemStore) = route("departments") {
    get("tree") {
        call.respond(store.read(call.bearer(), departmentRead) { db, _ ->
            val entries = db.rows("SELECT * FROM sys_department ORDER BY sort_order, id", map = ::hierarchy).groupBy { it.parent }
            fun tree(parent: String?): List<DepartmentNode> = entries[parent].orEmpty().map {
                DepartmentNode(it.id, it.name, it.order, tree(it.id))
            }
            tree(null)
        })
    }
    get("{id}/members") {
        val window = call.window()
        val department = call.id()
        val roleId = call.request.queryParameters["roleId"]?.takeIf(String::isNotBlank)
        call.respond(store.read(call.bearer(), userRead) { db, actor ->
            db.userPage(department, roleId, window, actor)
        })
    }
    post {
        val token = call.bearer()
        val request = call.receive<DepartmentRequest>()
        val id = newId()
        saveDepartment(store, token, id, request, create = true)
        call.respond(HttpStatusCode.Created, Created(id))
    }
    put("{id}") {
        val token = call.bearer()
        val id = call.id()
        saveDepartment(store, token, id, call.receive<DepartmentRequest>(), create = false)
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun hierarchy(row: ResultSet) =
    HierarchyRow(row.getString("id"), row.getString("parent_id"), row.getString("name"), row.getInt("sort_order"))

private fun validateParent(id: String, parent: String?, parents: Map<String, String?>) {
    var next = parent
    val visited = mutableSetOf(id)
    while (next != null) {
        if (!visited.add(next)) invalid("不能形成循环的上下级关系")
        if (next !in parents) invalid("上级节点不存在")
        next = parents[next]
    }
}

private suspend fun saveDepartment(store: SystemStore, token: String, id: String, request: DepartmentRequest, create: Boolean) {
    store.write(token, departmentWrite, administratorOnly = true) { db, _, audit ->
        val parentById = db.rows("SELECT id, parent_id FROM sys_department") { it.getString(1) to it.getString(2) }.toMap()
        if (!create && id !in parentById) missing("部门")
        validateParent(id, request.parentId, parentById)
        val name = checkedName(request.name)
        if (create) db.change(
            "INSERT INTO sys_department (id, parent_id, name, sort_order) VALUES (?, ?, ?, ?)",
            id, request.parentId, name, request.sortOrder,
        ) else db.change(
            "UPDATE sys_department SET parent_id = ?, name = ?, sort_order = ? WHERE id = ?",
            request.parentId, name, request.sortOrder, id,
        )
        audit(if (create) "department.create" else "department.update", id)
    }
}

internal data class UserVisibility(val condition: String, val parameters: List<String>)

/** SQL 片段使用用户表别名 u；调用方在验证操作权限后绑定 parameters。 */
internal fun CurrentUser.userVisibility(authority: String): UserVisibility = when (permissions[authority]) {
    "ALL" -> UserVisibility("1 = 1", emptyList())
    "DEPARTMENT" -> if (departmentId == null) UserVisibility("u.id = ?", listOf(id))
        else UserVisibility("u.department_id = ?", listOf(departmentId))
    "DEPARTMENT_AND_DESCENDANTS" -> if (departmentId == null) UserVisibility("u.id = ?", listOf(id))
        else UserVisibility(
            """u.department_id IN (
                WITH RECURSIVE descendants(id) AS (
                    SELECT id FROM sys_department WHERE id = ?
                    UNION SELECT d.id FROM sys_department d JOIN descendants a ON d.parent_id = a.id
                ) SELECT id FROM descendants
            )""",
            listOf(departmentId),
        )
    else -> UserVisibility("u.id = ?", listOf(id))
}

internal fun Connection.userPage(department: String?, roleId: String?, window: Window, actor: CurrentUser): Page<UserEntry> {
    val visibility = actor.userVisibility(userRead)
    val byDepartment = if (department == null) "" else " AND u.department_id = ?"
    val byRole = if (roleId == null) "" else " AND EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = ?)"
    val sql = "FROM sys_user u WHERE (${visibility.condition})$byDepartment$byRole"
    val args = (visibility.parameters + listOfNotNull(department, roleId)).toTypedArray()
    val total = rows("SELECT COUNT(*) $sql", *args) { it.getLong(1) }.single()
    val users = rows(
        "SELECT u.id, u.username, u.display_name, u.department_id, u.enabled $sql ORDER BY u.username, u.id LIMIT ? OFFSET ?",
        *args, window.size, window.offset,
    ) { User(it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getInt(5) == 1) }
    if (users.isEmpty()) return window.result(emptyList(), total)
    val placeholders = users.joinToString(",") { "?" }
    val roles = rows(
        """SELECT ur.user_id, r.id, r.code, r.name FROM sys_user_role ur
           JOIN sys_role r ON r.id = ur.role_id WHERE ur.user_id IN ($placeholders) ORDER BY r.code, r.id""",
        *users.map { it.id }.toTypedArray(),
    ) { it.getString(1) to AssignedRole(it.getString(2), it.getString(3), it.getString(4)) }.groupBy({ it.first }, { it.second })
    return window.result(users.map { UserEntry(it, roles[it.id].orEmpty()) }, total)
}
