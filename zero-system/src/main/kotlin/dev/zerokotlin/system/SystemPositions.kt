package dev.zerokotlin.system

import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
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

const val POSITION_READ = "system:position:read"
const val POSITION_WRITE = "system:position:write"

data class CreatePositionRequest(val code: String, val name: String, val sortOrder: Int?)
data class UpdatePositionRequest(val name: String, val enabled: Boolean?, val sortOrder: Int?)
data class ReplacePositionMembersRequest(val userIds: Set<String?>)
data class PositionEntry(val id: String, val code: String, val name: String, val enabled: Boolean, val sortOrder: Int)
data class PositionMember(val userId: String, val username: String, val displayName: String)

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

@RestController
@RequestMapping("/api/system/positions")
class SystemPositionController(private val positions: SystemPositions) {
    @GetMapping
    @PreAuthorize("hasAuthority('$POSITION_READ')")
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResult<PositionEntry> = positions.list(PageWindow(page, size))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$POSITION_WRITE')")
    fun create(@RequestBody request: CreatePositionRequest): CreatedResource = positions.create(request)

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$POSITION_WRITE')")
    fun update(@PathVariable id: String, @RequestBody request: UpdatePositionRequest) = positions.update(id, request)

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('$POSITION_READ')")
    fun members(@PathVariable id: String): List<PositionMember> = positions.members(id)

    @PutMapping("/{id}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$POSITION_WRITE')")
    fun replaceMembers(@PathVariable id: String, @RequestBody request: ReplacePositionMembersRequest) =
        positions.replaceMembers(id, request)
}
