package dev.zerokotlin.system.service.impl

import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.CreatePositionRequest
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.PositionEntry
import dev.zerokotlin.system.dto.PositionMember
import dev.zerokotlin.system.dto.ReplacePositionMembersRequest
import dev.zerokotlin.system.dto.UpdatePositionRequest
import dev.zerokotlin.system.entity.PositionRecord
import dev.zerokotlin.system.entity.UserRecord
import dev.zerokotlin.system.mapper.PositionMapper
import dev.zerokotlin.system.mapper.UserMapper
import dev.zerokotlin.system.security.POSITION_WRITE
import dev.zerokotlin.system.security.currentSystemActor
import dev.zerokotlin.system.service.SystemPositionService
import dev.zerokotlin.system.service.SystemWrites
import dev.zerokotlin.system.support.checkedCode
import dev.zerokotlin.system.support.checkedText
import dev.zerokotlin.system.support.conflict
import dev.zerokotlin.system.support.invalid
import dev.zerokotlin.system.support.missing
import dev.zerokotlin.system.support.newId
import dev.zerokotlin.system.support.query
import dev.zerokotlin.system.support.requiredFlag
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 查询岗位与成员，维护岗位资料和成员名单。写入和操作审计在同一事务提交。
 *
 * @property positions 读写岗位及岗位成员关系。
 * @property users 读取岗位成员，验证待替换名单中的用户是否存在。
 * @property writes 串行化写入，核验岗位写入权限和系统管理员身份，并记录操作审计。
 */
@Service
class SystemPositionServiceImpl(
    private val users: UserMapper,
    private val writes: SystemWrites,
) : ServiceImpl<PositionMapper, PositionRecord>(), SystemPositionService {
    private val positions: PositionMapper get() = getBaseMapper()

    /**
     * 在只读事务中查询岗位目录。
     *
     * @param window 页码和每页条数。
     * @return 按排序值和 ID 升序排列的岗位页，包含停用岗位。
     */
    @Transactional(readOnly = true)
    override fun list(window: PageWindow): PageResult<PositionEntry> {
        val results = positions.selectPage(
            Page<PositionRecord>(window.page.toLong(), window.size.toLong()),
            query<PositionRecord>().orderByAsc("sort_order", "id"),
        )
        val entries = results.records.map { PositionEntry(it.id, it.code, it.name, it.enabled == 1, it.sortOrder) }
        return window.result(entries, results.total)
    }

    /**
     * 在只读事务中查询岗位的完整成员名单，仅系统管理员可执行。
     *
     * @param id 岗位 ID，不存在时返回 404。
     * @return 按用户名和 ID 升序排列的成员摘要，包含停用账号。
     */
    @Transactional(readOnly = true)
    override fun members(id: String): List<PositionMember> {
        currentSystemActor().requireAdministrator()
        val position = positions.selectById(id) ?: missing("岗位")
        return users.findPositionMembers(position.id).map { PositionMember(it.id, it.username, it.displayName) }
    }

    /**
     * 校验写入权限和系统管理员身份后创建岗位。编码重复时返回 409。
     *
     * @param request 岗位的唯一编码、名称及必填的排序值。
     * @return 新岗位 ID。
     */
    @Transactional
    override fun create(request: CreatePositionRequest): CreatedResource {
        val actor = writes.authorize(POSITION_WRITE, administratorOnly = true)
        val code = checkedCode(request.code)
        if (positions.selectCount(query<PositionRecord>().eq("code", code)) != 0L) conflict("岗位编码已存在")

        val position = PositionRecord(
            id = newId(),
            code = code,
            name = checkedText(request.name, "name"),
            sortOrder = request.sortOrder ?: invalid("sortOrder 为必填项"),
        )
        positions.insert(position)
        writes.record(actor, "position.create", position.id)
        return CreatedResource(position.id)
    }

    /**
     * 校验写入权限和系统管理员身份后更新岗位资料。
     *
     * @param id 待修改的岗位 ID，不存在时返回 404。
     * @param request 新名称、必填的启用状态和排序值。
     */
    @Transactional
    override fun update(id: String, request: UpdatePositionRequest) {
        val actor = writes.authorize(POSITION_WRITE, administratorOnly = true)
        val position = positions.selectById(id) ?: missing("岗位")
        position.name = checkedText(request.name, "name")
        position.enabled = request.enabled.requiredFlag()
        position.sortOrder = request.sortOrder ?: invalid("sortOrder 为必填项")
        positions.updateById(position)
        writes.record(actor, "position.update", position.id)
    }

    /**
     * 系统管理员提交完整名单。全部用户校验通过后，在同一事务内替换岗位成员并记审计。
     *
     * @param id 待调整的岗位 ID，不存在时返回 404。
     * @param request 已存在用户的 ID 集合，每个 ID 长度须为 1～36，空集表示清空成员。
     */
    @Transactional
    override fun replaceMembers(id: String, request: ReplacePositionMembersRequest) {
        val actor = writes.authorize(POSITION_WRITE, administratorOnly = true)
        val position = positions.selectById(id) ?: missing("岗位")
        val userIds = request.userIds.map { it?.takeIf { id -> id.length in 1..36 } ?: invalid("用户 ID 无效") }

        // 完整名单可能超过数据库的单条语句参数上限。
        for (batch in userIds.chunked(500)) {
            val found = users.selectList(query<UserRecord>().select("id").`in`("id", batch)).map { it.id }.toSet()
            if (found != batch.toSet()) invalid("用户不存在")
        }

        positions.deleteMembers(position.id)
        userIds.forEach { positions.insertMember(position.id, it) }
        writes.record(actor, "position.members.replace", position.id)
    }
}
