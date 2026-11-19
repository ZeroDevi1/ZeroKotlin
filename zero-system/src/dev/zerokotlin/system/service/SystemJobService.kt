package dev.zerokotlin.system.service

import com.baomidou.mybatisplus.spring.service.IService
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.JobEntry
import dev.zerokotlin.system.dto.JobRunEntry
import dev.zerokotlin.system.dto.RunJobRequest
import dev.zerokotlin.system.dto.SaveJobRequest
import dev.zerokotlin.system.entity.ScheduledJobRecord

/** 提供定时任务与执行记录相关的业务操作。 */
interface SystemJobService : IService<ScheduledJobRecord> {
    fun list(window: PageWindow): PageResult<JobEntry>
    fun logs(window: PageWindow, jobId: String?): PageResult<JobRunEntry>
    fun create(request: SaveJobRequest): CreatedResource
    fun update(id: String, request: SaveJobRequest)
    fun delete(id: String)
    fun setPaused(id: String, paused: Boolean)
    fun runNow(id: String, request: RunJobRequest): CreatedResource
}
