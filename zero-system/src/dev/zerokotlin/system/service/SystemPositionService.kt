package dev.zerokotlin.system.service

import com.baomidou.mybatisplus.spring.service.IService
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.CreatePositionRequest
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.PositionEntry
import dev.zerokotlin.system.dto.PositionMember
import dev.zerokotlin.system.dto.ReplacePositionMembersRequest
import dev.zerokotlin.system.dto.UpdatePositionRequest
import dev.zerokotlin.system.entity.PositionRecord

/** 提供岗位与成员关系相关的业务操作。 */
interface SystemPositionService : IService<PositionRecord> {
    fun list(window: PageWindow): PageResult<PositionEntry>
    fun members(id: String): List<PositionMember>
    fun create(request: CreatePositionRequest): CreatedResource
    fun update(id: String, request: UpdatePositionRequest)
    fun replaceMembers(id: String, request: ReplacePositionMembersRequest)
}
