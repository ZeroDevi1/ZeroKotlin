package dev.zerokotlin.system.service.impl

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.DepartmentNode
import dev.zerokotlin.system.dto.DepartmentRequest
import dev.zerokotlin.system.entity.DepartmentRecord
import dev.zerokotlin.system.mapper.DepartmentMapper
import dev.zerokotlin.system.security.DEPARTMENT_WRITE
import dev.zerokotlin.system.service.SystemDepartmentService
import dev.zerokotlin.system.service.SystemWrites
import dev.zerokotlin.system.support.checkedText
import dev.zerokotlin.system.support.missing
import dev.zerokotlin.system.support.newId
import dev.zerokotlin.system.support.query
import dev.zerokotlin.system.support.validateParent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 构建部门树，维护部门资料和上下级关系。写入及审计在同一事务提交。
 *
 * @property departments 读取部门层级并保存部门节点。
 * @property writes 串行化部门写入，校验权限并记录审计。
 */
@Service
class SystemDepartmentServiceImpl(
    private val writes: SystemWrites,
) : ServiceImpl<DepartmentMapper, DepartmentRecord>(), SystemDepartmentService {
    private val departments: DepartmentMapper get() = getBaseMapper()

    /**
     * 按上级部门分组，递归构建组织架构。
     *
     * @return 部门树的根节点列表，同一父节点下按排序值和 ID 升序排列。
     */
    override fun tree(): List<DepartmentNode> {
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
    override fun create(request: DepartmentRequest): CreatedResource {
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
    override fun update(id: String, request: DepartmentRequest) {
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
