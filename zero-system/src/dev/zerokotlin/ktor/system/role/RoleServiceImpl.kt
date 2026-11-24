package dev.zerokotlin.ktor.system.role

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.plugins.pagination.Page as MybatisPage
import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.Page
import dev.zerokotlin.ktor.SystemStore
import dev.zerokotlin.ktor.Window
import dev.zerokotlin.ktor.administratorRole
import dev.zerokotlin.ktor.checkedDeleteIds
import dev.zerokotlin.ktor.checkedName
import dev.zerokotlin.ktor.exists
import dev.zerokotlin.ktor.flag
import dev.zerokotlin.ktor.invalid
import dev.zerokotlin.ktor.missing
import dev.zerokotlin.ktor.newId
import dev.zerokotlin.ktor.permissionPattern
import dev.zerokotlin.ktor.scopeOrder
import dev.zerokotlin.ktor.system.account.userManage
import java.sql.Connection

/** 角色记录、权限/菜单关联以及审计在一个写事务内维护。 */
internal class RoleServiceImpl(private val store: SystemStore) : RoleService {
    private val read = "system:role:read"
    private val write = "system:role:write"
    private val administratorPermissions = setOf(
        userManage, write, "system:permission:write", "system:department:write", "system:position:write",
    )

    override suspend fun list(token: String, window: Window, enabled: Boolean?): Page<Role> =
        store.read(token, read) { db, _ ->
            mapper(db) { roles ->
                val query = QueryWrapper<RoleRecord>().orderByAsc("code", "id")
                if (enabled != null) query.eq("enabled", enabled.flag())
                val page = roles.selectPage(MybatisPage<RoleRecord>(window.page.toLong(), window.size.toLong()), query)
                window.result(page.records.map(RoleRecord::summary), page.total)
            }
        }

    override suspend fun create(token: String, request: CreateRole): Created =
        store.write(token, write, administratorOnly = true) { db, _, audit ->
            val code = request.code.takeIf(permissionPattern::matches) ?: invalid("角色编码无效")
            val id = newId()
            mapper(db) { roles ->
                if (roles.selectCount(QueryWrapper<RoleRecord>().eq("code", code)) > 0L) throw SystemFailure(409, "角色编码已存在")
                check(roles.insert(RoleRecord().also {
                    it.id = id
                    it.code = code
                    it.name = checkedName(request.name)
                    it.dataScope = checkedScope(request.dataScope)
                }) == 1)
            }
            db.savePermissions(id, request.permissionCodes)
            audit("role.create", id)
            Created(id)
        }

    override suspend fun detail(token: String, id: String): RoleDetail =
        store.read(token, read) { db, _ ->
            val role = mapper(db) { it.selectById(id) } ?: missing("角色")
            RoleDetail(role.summary(), db.rolePermissionCodes(id))
        }

    override suspend fun update(token: String, id: String, request: UpdateRole) {
        store.write(token, write, administratorOnly = true) { db, _, audit ->
            mapper(db) { roles ->
                roles.requireBusinessRole(id)
                roles.updateById(RoleRecord().also {
                    it.id = id
                    it.name = checkedName(request.name)
                    it.dataScope = checkedScope(request.dataScope)
                    it.enabled = request.enabled.flag()
                })
            }
            db.savePermissions(id, request.permissionCodes)
            audit("role.update", id)
        }
    }

    override suspend fun delete(token: String, ids: List<String?>) {
        store.write(token, write, administratorOnly = true) { db, _, audit ->
            val targets = checkedDeleteIds(ids)
            mapper(db) { roles ->
                targets.forEach { id ->
                    val role = roles.requireBusinessRole(id)
                    if (exists(db, "SELECT 1 FROM sys_user_role WHERE role_id = ?", id)) {
                        throw SystemFailure(409, "角色 ${role.code} 仍分配给用户")
                    }
                }
                targets.forEach { id ->
                    db.replaceRolePermissions(id, emptySet())
                    db.replaceRoleMenus(id, emptySet())
                    roles.deleteById(id)
                    audit("role.delete", id)
                }
            }
        }
    }

    override suspend fun menus(token: String, id: String): RoleMenus =
        store.read(token, read) { db, _ ->
            val role = mapper(db) { it.requireBusinessRole(id) }
            val mode = if (role.directMenus == 1) MenuMode.DIRECT else MenuMode.LEGACY
            RoleMenus(mode, db.roleMenuIds(id))
        }

    override suspend fun updateMenus(token: String, id: String, request: RoleMenus) {
        store.write(token, write, administratorOnly = true) { db, _, audit ->
            mapper(db) { roles ->
                roles.requireBusinessRole(id)
                if (request.mode == MenuMode.LEGACY && request.menuIds.isNotEmpty()) invalid("沿用权限点规则时须清空直接菜单")
                if (request.menuIds.size > 256) invalid("单个角色最多直接分配 256 个菜单")
                if (request.menuIds.any { !exists(db, "SELECT 1 FROM sys_menu WHERE id = ?", it) }) invalid("菜单不存在")
                roles.updateById(RoleRecord().also {
                    it.id = id
                    it.directMenus = (request.mode == MenuMode.DIRECT).flag()
                })
            }
            db.replaceRoleMenus(id, request.menuIds)
            audit("role.menus.update", id)
        }
    }

    private fun checkedScope(value: String) = value.takeIf { it in scopeOrder } ?: invalid("角色数据范围无效")

    private fun RoleMapper.requireBusinessRole(id: String): RoleRecord =
        (selectById(id) ?: missing("角色")).also {
            if (id == administratorRole) invalid("内置管理员角色由系统维护")
        }

    private fun Connection.savePermissions(id: String, codes: Set<String>) {
        if (codes.size > 256) invalid("单个角色最多关联 256 个权限点")
        if (codes.any { it in administratorPermissions }) invalid("所选权限由内置管理员专用")
        if (codes.any { !permissionPattern.matches(it) || !exists(this, "SELECT 1 FROM sys_permission WHERE code = ?", it) }) {
            invalid("权限点不存在或编码无效")
        }
        replaceRolePermissions(id, codes)
    }

    private fun <T> mapper(db: Connection, action: (RoleMapper) -> T): T =
        store.mappers.withSession(db) { action(it.getMapper(RoleMapper::class.java)) }
}
