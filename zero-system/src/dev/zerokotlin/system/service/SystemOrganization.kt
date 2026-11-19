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

/**
 * 构建部门树，维护部门资料和上下级关系。写入及审计在同一事务提交。
 *
 * @property departments 读取部门层级并保存部门节点。
 * @property writes 串行化部门写入，校验权限并记录审计。
 */
@Service
class SystemDepartments(
    private val departments: DepartmentMapper,
    private val writes: SystemWrites,
) {
    /**
     * 按上级部门分组，递归构建组织架构。
     *
     * @return 部门树的根节点列表，同一父节点下按排序值和 ID 升序排列。
     */
    fun tree(): List<DepartmentNode> {
        val children = departments.selectList(query<DepartmentRecord>().orderByAsc("sort_order", "id"))
            .groupBy { it.parentId }

        /**
         * 递归构建指定父节点下的部门分支。
         *
         * @param parentId 上级部门 ID，为 null 时处理根层级。
         * @return 指定层级的部门节点，各节点包含其子树。
         */
        fun branch(parentId: String?): List<DepartmentNode> =
            children[parentId].orEmpty().map { DepartmentNode(it.id, it.name, it.sortOrder, branch(it.id)) }

        return branch(null)
    }

    /**
     * 校验部门写入权限和系统管理员身份后新增部门。
     *
     * @param request 部门名称、排序值及可选的上级部门 ID。
     * @return 新部门 ID。
     */
    @Transactional
    fun create(request: DepartmentRequest): CreatedResource {
        val actor = writes.authorize(DEPARTMENT_WRITE, administratorOnly = true)
        val id = newId()
        save(id, request, creating = true)
        writes.record(actor, "department.create", id)
        return CreatedResource(id)
    }

    /**
     * 校验部门写入权限和系统管理员身份后更新部门。
     *
     * @param id 待修改的部门 ID，不存在时返回 404。
     * @param request 新的名称、同级排序值及上级部门关系。
     */
    @Transactional
    fun update(id: String, request: DepartmentRequest) {
        val actor = writes.authorize(DEPARTMENT_WRITE, administratorOnly = true)
        save(id, request, creating = false)
        writes.record(actor, "department.update", id)
    }

    /**
     * 在写入锁下校验部门名称和新上级的祖先链，然后保存部门。
     *
     * @param id 新增或更新的部门 ID。
     * @param request 待保存的资料。上级 ID 为 null 时保存为根部门。
     * @param creating 为 true 时插入，为 false 时要求目标已存在并更新。
     */
    private fun save(id: String, request: DepartmentRequest, creating: Boolean) {
        val rows = departments.selectList(null)
        if (!creating && rows.none { it.id == id }) missing("部门")
        validateParent(id, request.parentId, rows, DepartmentRecord::id, DepartmentRecord::parentId)

        val row = DepartmentRecord(id, request.parentId, checkedText(request.name, "name"), request.sortOrder)
        if (creating) departments.insert(row) else departments.updateById(row)
    }
}

/**
 * 构建管理端菜单和按会话过滤的导航菜单，维护菜单层级与权限关联。
 *
 * @property menus 读写菜单节点及父子关系。
 * @property permissions 核验菜单引用的权限编码是否存在。
 * @property writes 串行化菜单写入，校验权限并将变更与审计一并提交。
 * @param modules 提供已注册的模块迁移描述，从中提取可用模块名。
 */
@Service
class SystemMenus(
    private val menus: MenuMapper,
    private val permissions: PermissionMapper,
    private val writes: SystemWrites,
    modules: ObjectProvider<ModuleMigration>,
) {
    /** 当前应用中已注册迁移描述的模块名集合，用于菜单可见性判断。 */
    private val installedModules = modules.stream().map { it.name }.toList().toSet()

    /**
     * 读取管理用菜单树。
     *
     * @return 从根节点可达的全部菜单，包含停用或设有访问权限的节点。
     */
    fun tree(): List<MenuNode> = buildTree { true }

    /**
     * 按启用状态、模块是否可用及会话是否持有菜单权限筛选节点。
     *
     * @param actor 当前会话主体，使用持有的权限编码判断菜单可见性。
     * @return 筛选后从根节点可达的菜单树。父节点被筛除时，其分支也不会出现在结果中。
     */
    fun visibleTree(actor: SystemActor): List<MenuNode> =
        buildTree {
            it.enabled == 1 && (it.moduleName == null || it.moduleName in installedModules) &&
                (it.permissionCode == null || it.permissionCode in actor.scopes)
        }

    /**
     * 先筛选节点，再按父节点分组并从根层级递归构建。
     *
     * @param visible 判定单条菜单记录是否参与建树。
     * @return 菜单树的根节点列表，同一父节点下按排序值和 ID 升序排列。
     */
    private fun buildTree(visible: (MenuRecord) -> Boolean): List<MenuNode> {
        val children = menus.selectList(query<MenuRecord>().orderByAsc("sort_order", "id"))
            .filter(visible).groupBy { it.parentId }

        /**
         * 递归构建指定父节点下的已筛选菜单分支。
         *
         * @param parentId 父菜单 ID，为 null 时处理根层级。
         * @return 指定层级的菜单节点，各节点包含其可达子树。
         */
        fun branch(parentId: String?): List<MenuNode> = children[parentId].orEmpty().map {
            MenuNode(it.id, it.name, it.path, it.permissionCode, it.sortOrder, it.enabled == 1, branch(it.id))
        }

        return branch(null)
    }

    /**
     * 校验菜单写入权限后创建节点并记录审计。
     *
     * @param request 菜单名称、父节点、可选路径和权限编码、排序值及启用状态。
     * @return 新菜单 ID。
     */
    @Transactional
    fun create(request: MenuRequest): CreatedResource {
        val actor = writes.authorize(MENU_WRITE)
        val id = newId()
        save(id, request, creating = true)
        writes.record(actor, "menu.create", id)
        return CreatedResource(id)
    }

    /**
     * 校验菜单写入权限后更新节点并记录审计。
     *
     * @param id 待更新的菜单 ID，不存在时返回 404。
     * @param request 待保存的菜单属性和父节点关系。
     */
    @Transactional
    fun update(id: String, request: MenuRequest) {
        val actor = writes.authorize(MENU_WRITE)
        save(id, request, creating = false)
        writes.record(actor, "menu.update", id)
    }

    /**
     * 在写入锁下校验父节点链、可选权限和站内路径，再保存节点。
     *
     * @param id 新增或更新的菜单 ID。
     * @param request 待保存的属性。父节点为 null 时保存为根菜单。
     * @param creating 为 true 时插入，为 false 时要求目标已存在并更新。
     */
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
