package dev.zerokotlin.system.service

import com.baomidou.mybatisplus.spring.service.IService
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.AnnouncementEntry
import dev.zerokotlin.system.dto.AnnouncementRequest
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.entity.AnnouncementRecord

/** 提供公告及其发布状态相关的业务操作。 */
interface SystemAnnouncementService : IService<AnnouncementRecord> {
    fun list(window: PageWindow): PageResult<AnnouncementEntry>
    fun create(request: AnnouncementRequest): CreatedResource
    fun update(id: String, request: AnnouncementRequest)
    fun publish(id: String)
    fun revoke(id: String)
}
