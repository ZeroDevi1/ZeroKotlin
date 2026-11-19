package dev.zerokotlin.system.service

import dev.zerokotlin.core.ModuleMigration
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.DepartmentNode
import dev.zerokotlin.system.dto.DepartmentRequest
import dev.zerokotlin.system.dto.MenuNode
import dev.zerokotlin.system.dto.MenuRequest
import dev.zerokotlin.system.entity.DepartmentRecord
import dev.zerokotlin.system.entity.MenuRecord
import dev.zerokotlin.system.mapper.DepartmentMapper
import dev.zerokotlin.system.mapper.MenuMapper
import dev.zerokotlin.system.mapper.PermissionMapper
import dev.zerokotlin.system.security.DEPARTMENT_WRITE
import dev.zerokotlin.system.security.MENU_WRITE
import dev.zerokotlin.system.security.SystemActor
import dev.zerokotlin.system.support.checkedCode
import dev.zerokotlin.system.support.checkedText
import dev.zerokotlin.system.support.flag
import dev.zerokotlin.system.support.invalid
import dev.zerokotlin.system.support.missing
import dev.zerokotlin.system.support.newId
import dev.zerokotlin.system.support.query
import dev.zerokotlin.system.support.validateParent
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SystemDepartments(
    private val departments: DepartmentMapper,
    private val writes: SystemWrites,
) {
    fun tree(): List<DepartmentNode> {
        val children = departments.selectList(query<DepartmentRecord>().orderByAsc("sort_order", "id"))
            .groupBy { it.parentId }

        fun branch(parentId: String?): List<DepartmentNode> =
            children[parentId].orEmpty().map { DepartmentNode(it.id, it.name, it.sortOrder, branch(it.id)) }

        return branch(null)
    }

    @Transactional
    fun create(request: DepartmentRequest): CreatedResource {
        val actor = writes.authorize(DEPARTMENT_WRITE, administratorOnly = true)
        val id = newId()
        save(id, request, creating = true)
        writes.record(actor, "department.create", id)
        return CreatedResource(id)
    }

    @Transactional
    fun update(id: String, request: DepartmentRequest) {
        val actor = writes.authorize(DEPARTMENT_WRITE, administratorOnly = true)
        save(id, request, creating = false)
        writes.record(actor, "department.update", id)
    }

    private fun save(id: String, request: DepartmentRequest, creating: Boolean) {
        val rows = departments.selectList(null)
        if (!creating && rows.none { it.id == id }) missing("部门")
        validateParent(id, request.parentId, rows, DepartmentRecord::id, DepartmentRecord::parentId)

        val row = DepartmentRecord(id, request.parentId, checkedText(request.name, "name"), request.sortOrder)
        if (creating) departments.insert(row) else departments.updateById(row)
    }
}

@Service
class SystemMenus(
    private val menus: MenuMapper,
    private val permissions: PermissionMapper,
    private val writes: SystemWrites,
    modules: ObjectProvider<ModuleMigration>,
) {
    private val installedModules = modules.stream().map { it.name }.toList().toSet()

    fun tree(): List<MenuNode> = buildTree { true }

    fun visibleTree(actor: SystemActor): List<MenuNode> =
        buildTree {
            it.enabled == 1 && (it.moduleName == null || it.moduleName in installedModules) &&
                (it.permissionCode == null || it.permissionCode in actor.scopes)
        }

    private fun buildTree(visible: (MenuRecord) -> Boolean): List<MenuNode> {
        val children = menus.selectList(query<MenuRecord>().orderByAsc("sort_order", "id"))
            .filter(visible).groupBy { it.parentId }

        fun branch(parentId: String?): List<MenuNode> = children[parentId].orEmpty().map {
            MenuNode(it.id, it.name, it.path, it.permissionCode, it.sortOrder, it.enabled == 1, branch(it.id))
        }

        return branch(null)
    }

    @Transactional
    fun create(request: MenuRequest): CreatedResource {
        val actor = writes.authorize(MENU_WRITE)
        val id = newId()
        save(id, request, creating = true)
        writes.record(actor, "menu.create", id)
        return CreatedResource(id)
    }

    @Transactional
    fun update(id: String, request: MenuRequest) {
        val actor = writes.authorize(MENU_WRITE)
        save(id, request, creating = false)
        writes.record(actor, "menu.update", id)
    }

    private fun save(id: String, request: MenuRequest, creating: Boolean) {
        val rows = menus.selectList(null)
        if (!creating && rows.none { it.id == id }) missing("菜单")
        validateParent(id, request.parentId, rows, MenuRecord::id, MenuRecord::parentId)
        val permission = request.permissionCode?.let(::checkedCode)
        if (permission != null && permissions.selectById(permission)?.code != permission) invalid("权限点不存在")
        val path = request.path?.let { checkedText(it, "path", 255) }
        if (path != null && (!path.startsWith("/") || path.startsWith("//") ||
                path.any { it == '\\' || Character.isISOControl(it) })) {
            invalid("菜单路径须为站内路径")
        }

        val row = MenuRecord(
            id, request.parentId, checkedText(request.name, "name"), path, permission, request.sortOrder,
            request.enabled.flag(),
        )
        if (creating) menus.insert(row) else menus.updateById(row)
    }
}
