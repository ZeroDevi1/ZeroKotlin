package dev.zerokotlin.system.service.impl

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl
import dev.zerokotlin.system.dto.CreatePermissionRequest
import dev.zerokotlin.system.dto.PermissionSummary
import dev.zerokotlin.system.dto.UpdatePermissionRequest
import dev.zerokotlin.system.entity.PermissionRecord
import dev.zerokotlin.system.mapper.PermissionMapper
import dev.zerokotlin.system.security.PERMISSION_WRITE
import dev.zerokotlin.system.service.SystemPermissionService
import dev.zerokotlin.system.service.SystemWrites
import dev.zerokotlin.system.support.checkedCode
import dev.zerokotlin.system.support.checkedText
import dev.zerokotlin.system.support.conflict
import dev.zerokotlin.system.support.invalid
import dev.zerokotlin.system.support.missing
import dev.zerokotlin.system.support.query
import dev.zerokotlin.system.support.requiredFlag
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 将权限点记录转换为业务摘要。
 *
 * @receiver 待转换的权限点记录。
 * @return 权限点编码、名称及布尔启用状态。
 */
private fun PermissionRecord.summary() = PermissionSummary(code, name, enabled == 1)

/**
 * 查询权限目录并维护自定义权限点。权限点写入要求系统管理员身份和权限点写入权限。
 *
 * @property permissions 按编码读写权限点及启用状态。
 * @property writes 串行化权限点写入，重新授权并记录操作审计。
 */
@Service
class SystemPermissionServiceImpl(
    private val writes: SystemWrites,
) : ServiceImpl<PermissionMapper, PermissionRecord>(), SystemPermissionService {
    private val permissions: PermissionMapper get() = getBaseMapper()

    /**
     * 查询权限点目录。
     *
     * @return 按编码升序排列的全部权限点摘要。
     */
    override fun summaries(): List<PermissionSummary> =
        permissions.selectList(query<PermissionRecord>().orderByAsc("code")).map(PermissionRecord::summary)

    /**
     * 校验自定义编码后登记权限点，并在同一事务写入审计。编码重复时返回 409。
     *
     * @param request 自定义权限编码和显示名称。
     * @return 新权限点摘要。
     */
    @Transactional
    override fun create(request: CreatePermissionRequest): PermissionSummary {
        val actor = writes.authorize(PERMISSION_WRITE, administratorOnly = true)
        val code = editableCode(request.code)
        if (permissions.selectById(code) != null) conflict("权限编码已存在")

        val permission = PermissionRecord(code, checkedText(request.name, "name"))
        permissions.insert(permission)
        writes.record(actor, "permission.create", code)
        return permission.summary()
    }

    /**
     * 更新自定义权限点的名称和启用状态，并在同一事务写入审计。
     *
     * @param code 待修改的自定义权限编码，不存在时返回 404。
     * @param request 新名称和必填的启用状态。
     * @return 更新后的权限点摘要。
     */
    @Transactional
    override fun update(code: String, request: UpdatePermissionRequest): PermissionSummary {
        val actor = writes.authorize(PERMISSION_WRITE, administratorOnly = true)
        val permission = permissions.selectById(editableCode(code)) ?: missing("权限点")

        permission.name = checkedText(request.name, "name")
        permission.enabled = request.enabled.requiredFlag()
        permissions.updateById(permission)
        writes.record(actor, "permission.update", code)
        return permission.summary()
    }

    /**
     * 校验编码格式及保留前缀。使用 system: 前缀或格式无效时抛出 HTTP 400 异常。
     *
     * @param value 请求提交的权限编码。
     * @return 通过校验的自定义权限编码。
     */
    private fun editableCode(value: String): String =
        checkedCode(value).also { if (it.startsWith("system:")) invalid("system: 权限由系统维护") }
}
