package dev.zerokotlin.system.service

import com.baomidou.mybatisplus.extension.plugins.pagination.Page
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

@Service
class SystemPositions(
    private val positions: PositionMapper,
    private val users: UserMapper,
    private val writes: SystemWrites,
) {
    @Transactional(readOnly = true)
    fun list(window: PageWindow): PageResult<PositionEntry> {
        val results = positions.selectPage(
            Page<PositionRecord>(window.page.toLong(), window.size.toLong()),
            query<PositionRecord>().orderByAsc("sort_order", "id"),
        )
        val entries = results.records.map { PositionEntry(it.id, it.code, it.name, it.enabled == 1, it.sortOrder) }
        return window.result(entries, results.total)
    }

    @Transactional(readOnly = true)
    fun members(id: String): List<PositionMember> {
        currentSystemActor().requireAdministrator()
        val position = positions.selectById(id) ?: missing("岗位")
        return users.findPositionMembers(position.id).map { PositionMember(it.id, it.username, it.displayName) }
    }

    @Transactional
    fun create(request: CreatePositionRequest): CreatedResource {
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

    @Transactional
    fun update(id: String, request: UpdatePositionRequest) {
        val actor = writes.authorize(POSITION_WRITE, administratorOnly = true)
        val position = positions.selectById(id) ?: missing("岗位")
        position.name = checkedText(request.name, "name")
        position.enabled = request.enabled.requiredFlag()
        position.sortOrder = request.sortOrder ?: invalid("sortOrder 为必填项")
        positions.updateById(position)
        writes.record(actor, "position.update", position.id)
    }

    @Transactional
    fun replaceMembers(id: String, request: ReplacePositionMembersRequest) {
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
