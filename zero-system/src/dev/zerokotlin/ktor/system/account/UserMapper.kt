package dev.zerokotlin.ktor.system.account

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.ktor.Page
import dev.zerokotlin.ktor.SystemStore
import dev.zerokotlin.ktor.Window
import dev.zerokotlin.ktor.administratorRole
import dev.zerokotlin.ktor.change
import dev.zerokotlin.ktor.exists
import dev.zerokotlin.ktor.invalid
import dev.zerokotlin.ktor.missing
import dev.zerokotlin.ktor.rows
import dev.zerokotlin.ktor.system.auth.CurrentUser
import dev.zerokotlin.ktor.userRead
import java.sql.Connection
import kotlinx.serialization.Serializable

@Serializable internal data class User(
    val id: String, val username: String, val displayName: String, val departmentId: String?, val enabled: Boolean,
)
@Serializable internal data class AssignedRole(val id: String, val code: String, val name: String)
@Serializable internal data class UserEntry(val user: User, val roles: List<AssignedRole>)

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

/** 列表与总数共享范围；角色信息按当前页批量读取。 */
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

/** 系统账号的持久化实体，常规写入使用 BaseMapper。API 读取使用显式投影以隔离凭据。 */
@TableName("sys_user")
internal class UserRecord {
    @field:TableId(type = IdType.INPUT)
    var id: String? = null
    var username: String? = null
    @field:TableField("display_name")
    var displayName: String? = null
    @field:TableField("password_hash")
    var passwordHash: String? = null
    @field:TableField("department_id")
    var departmentId: String? = null
    var enabled: Int? = null
}

internal interface UserMapper : BaseMapper<UserRecord>

internal fun Connection.visibleUser(id: String, actor: CurrentUser, authority: String): User {
    val visibility = actor.userVisibility(authority)
    return rows(
        """SELECT u.id, u.username, u.display_name, u.department_id, u.enabled
           FROM sys_user u WHERE u.id = ? AND (${visibility.condition})""", id, *visibility.parameters.toTypedArray(),
    ) { User(it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getInt(5) == 1) }
        .singleOrNull() ?: missing("用户")
}

internal fun Connection.userRoleIds(id: String) =
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

/** 用户创建和 Excel 导入共享同一条权限关系写入路径。 */
internal fun SystemStore.insertUser(
    db: Connection, id: String, username: String, displayName: String, hash: String,
    department: String?, roles: Set<String>,
) {
    mappers.withSession(db) { session ->
        val mapper = session.getMapper(UserMapper::class.java)
        if (mapper.selectCount(QueryWrapper<UserRecord>().eq("username", username)) > 0L) {
            throw SystemFailure(409, "用户名已存在")
        }
        db.validateDepartment(department)
        db.validateRoles(roles)
        val user = UserRecord().also {
            it.id = id
            it.username = username
            it.displayName = displayName
            it.passwordHash = hash
            it.departmentId = department
            it.enabled = 1
        }
        check(mapper.insert(user) == 1)
    }
    db.setUserRoles(id, roles)
}

internal fun Connection.deleteUserSessions(id: String) {
    change("DELETE FROM sys_session WHERE user_id = ?", id)
}

internal fun Connection.deleteUserRelations(id: String) {
    deleteUserSessions(id)
    change("DELETE FROM sys_user_role WHERE user_id = ?", id)
    change("DELETE FROM sys_user_position WHERE user_id = ?", id)
    change("DELETE FROM sys_announcement_read WHERE user_id = ?", id)
}
