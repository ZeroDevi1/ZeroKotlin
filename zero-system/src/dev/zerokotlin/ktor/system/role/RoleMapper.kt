package dev.zerokotlin.ktor.system.role

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.ktor.administratorRole
import dev.zerokotlin.ktor.change
import dev.zerokotlin.ktor.rows
import java.sql.Connection

/** 角色的常规操作由 MyBatis-Plus 执行，关联表写入沿用同一连接。 */
@TableName("sys_role")
internal class RoleRecord {
    @field:TableId(type = IdType.INPUT)
    var id: String? = null
    var code: String? = null
    var name: String? = null
    @field:TableField("data_scope")
    var dataScope: String? = null
    var enabled: Int? = null
    @field:TableField("direct_menus")
    var directMenus: Int? = null

    fun summary(): Role = Role(checkNotNull(id), checkNotNull(code), checkNotNull(name), checkNotNull(dataScope), enabled == 1)
}

internal interface RoleMapper : BaseMapper<RoleRecord>

internal fun Connection.rolePermissionCodes(id: String): List<String> =
    if (id == administratorRole) rows("SELECT code FROM sys_permission WHERE enabled = 1 ORDER BY code") { it.getString(1) }
    else rows("SELECT permission_code FROM sys_role_permission WHERE role_id = ? ORDER BY permission_code", id) { it.getString(1) }

internal fun Connection.replaceRolePermissions(id: String, codes: Set<String>) {
    change("DELETE FROM sys_role_permission WHERE role_id = ?", id)
    codes.forEach { change("INSERT INTO sys_role_permission (role_id, permission_code) VALUES (?, ?)", id, it) }
}

internal fun Connection.roleMenuIds(id: String) =
    rows("SELECT menu_id FROM sys_role_menu WHERE role_id = ? ORDER BY menu_id", id) { it.getString(1) }.toSet()

internal fun Connection.replaceRoleMenus(id: String, menus: Set<String>) {
    change("DELETE FROM sys_role_menu WHERE role_id = ?", id)
    menus.forEach { change("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)", id, it) }
}
