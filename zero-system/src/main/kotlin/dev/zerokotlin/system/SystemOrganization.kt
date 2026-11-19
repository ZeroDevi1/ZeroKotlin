package dev.zerokotlin.system

import dev.zerokotlin.core.ModuleMigration
import dev.zerokotlin.core.PageResult
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class DepartmentRequest(val name: String, val parentId: String? = null, val sortOrder: Int = 0)

data class DepartmentNode(val id: String, val name: String, val sortOrder: Int, val children: List<DepartmentNode>)

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

const val MENU_WRITE = "system:menu:write"

data class MenuRequest(
    val name: String,
    val parentId: String? = null,
    val path: String? = null,
    val permissionCode: String? = null,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
)

data class MenuNode(
    val id: String,
    val name: String,
    val path: String?,
    val permissionCode: String?,
    val sortOrder: Int,
    val enabled: Boolean,
    val children: List<MenuNode>,
)

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

@RestController
@RequestMapping("/api/system/departments")
class SystemDepartmentController(
    private val departments: SystemDepartments,
    private val users: SystemUsers,
) {
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:department:read')")
    fun tree(): List<DepartmentNode> = departments.tree()

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('$USER_READ')")
    fun members(
        @PathVariable id: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) roleId: String?,
    ): PageResult<UserListEntry> = users.list(PageWindow(page, size), id, roleId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$DEPARTMENT_WRITE')")
    fun create(@RequestBody request: DepartmentRequest): CreatedResource = departments.create(request)

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$DEPARTMENT_WRITE')")
    fun update(@PathVariable id: String, @RequestBody request: DepartmentRequest) = departments.update(id, request)
}

@RestController
@RequestMapping("/api/system/menus")
class SystemMenuController(private val menus: SystemMenus) {
    @GetMapping
    @PreAuthorize("hasAuthority('system:menu:read')")
    fun tree(): List<MenuNode> = menus.tree()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$MENU_WRITE')")
    fun create(@RequestBody request: MenuRequest): CreatedResource = menus.create(request)

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$MENU_WRITE')")
    fun update(@PathVariable id: String, @RequestBody request: MenuRequest) = menus.update(id, request)
}
