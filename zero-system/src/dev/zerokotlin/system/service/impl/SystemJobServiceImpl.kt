package dev.zerokotlin.system.service.impl

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.JobEntry
import dev.zerokotlin.system.dto.JobRunEntry
import dev.zerokotlin.system.dto.JobRunSource
import dev.zerokotlin.system.dto.JobRunStatus
import dev.zerokotlin.system.dto.RunJobRequest
import dev.zerokotlin.system.dto.SaveJobRequest
import dev.zerokotlin.system.entity.JobRunRecord
import dev.zerokotlin.system.entity.ScheduledJobRecord
import dev.zerokotlin.system.mapper.JobRunMapper
import dev.zerokotlin.system.mapper.ScheduledJobMapper
import dev.zerokotlin.system.security.JOB_CREATE
import dev.zerokotlin.system.security.JOB_DELETE
import dev.zerokotlin.system.security.JOB_PAUSE
import dev.zerokotlin.system.security.JOB_RESUME
import dev.zerokotlin.system.security.JOB_RUN
import dev.zerokotlin.system.security.JOB_UPDATE
import dev.zerokotlin.system.service.JobHandlers
import dev.zerokotlin.system.service.JobScheduleStorage
import dev.zerokotlin.system.service.SystemJobService
import dev.zerokotlin.system.service.SystemWrites
import dev.zerokotlin.system.service.nextFire
import dev.zerokotlin.system.support.checkedText
import dev.zerokotlin.system.support.conflict
import dev.zerokotlin.system.support.flag
import dev.zerokotlin.system.support.invalid
import dev.zerokotlin.system.support.missing
import dev.zerokotlin.system.support.newId
import dev.zerokotlin.system.support.query
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val requestIdPattern = Regex("[0-9a-f]{32}")

/** 管理任务定义、手动请求和日志。所有管理写入沿用系统事务锁与操作审计。 */
@Service
class SystemJobServiceImpl(
    private val runs: JobRunMapper,
    private val handlers: JobHandlers,
    private val schedules: JobScheduleStorage,
    private val writes: SystemWrites,
) : ServiceImpl<ScheduledJobMapper, ScheduledJobRecord>(), SystemJobService {
    private val jobs: ScheduledJobMapper get() = getBaseMapper()

    /** 按任务名称及 ID 升序返回任务目录。 */
    @Transactional(readOnly = true)
    override fun list(window: PageWindow): PageResult<JobEntry> {
        val result = jobs.selectPage(
            Page<ScheduledJobRecord>(window.page.toLong(), window.size.toLong()),
            query<ScheduledJobRecord>().orderByAsc("name", "id"),
        )
        return window.result(result.records.map {
            JobEntry(it.id, it.name, it.handler, schedules.read(it), it.paused == 1, it.nextFireAt)
        }, result.total)
    }

    /** 按请求时间和 ID 倒序读取日志。任务删除后仍可按原 ID 筛选。 */
    @Transactional(readOnly = true)
    override fun logs(window: PageWindow, jobId: String?): PageResult<JobRunEntry> {
        if (jobId != null && jobId.length !in 1..36) invalid("任务 ID 无效")
        val result = runs.selectPage(
            Page<JobRunRecord>(window.page.toLong(), window.size.toLong()),
            query<JobRunRecord>().eq(jobId != null, "job_id", jobId).orderByDesc("requested_at", "id"),
        )
        return window.result(result.records.map {
            JobRunEntry(
                it.id, it.jobId, it.jobName, it.handler, it.source, it.status,
                it.requestedAt, it.startedAt, it.finishedAt, it.message,
            )
        }, result.total)
    }

    /** 创建任务。包含触发器时计算首次自动派发时间。 */
    @Transactional
    override fun create(request: SaveJobRequest): CreatedResource {
        val actor = writes.authorize(JOB_CREATE)
        val job = ScheduledJobRecord(id = newId())
        applyDefinition(job, request)
        jobs.insert(job)
        writes.record(actor, "job.create", job.id)
        return CreatedResource(job.id)
    }

    /** 编辑任务。触发器配置变化时从当前时刻重新计算计划。 */
    @Transactional
    override fun update(id: String, request: SaveJobRequest) {
        val actor = writes.authorize(JOB_UPDATE)
        val job = requireJob(id)
        applyDefinition(job, request)
        jobs.updateById(job)
        writes.record(actor, "job.update", job.id)
    }

    /** 删除任务并取消尚未执行的手动请求，保留全部历史。 */
    @Transactional
    override fun delete(id: String) {
        val actor = writes.authorize(JOB_DELETE)
        val job = requireJob(id)
        runs.update(null, UpdateWrapper<JobRunRecord>()
            .eq("job_id", id).eq("status", JobRunStatus.QUEUED.name)
            .set("status", JobRunStatus.CANCELLED.name)
            .set("finished_at", System.currentTimeMillis())
            .set("message", "任务已删除"))
        jobs.deleteById(job.id)
        writes.record(actor, "job.delete", job.id)
    }

    /** 幂等地暂停或恢复自动计划。保持原有基准，跳过暂停期间已错过的时间点。 */
    @Transactional
    override fun setPaused(id: String, paused: Boolean) {
        val actor = writes.authorize(if (paused) JOB_PAUSE else JOB_RESUME)
        val job = requireJob(id)
        val schedule = schedules.read(job) ?: conflict("任务未配置自动触发器")
        if (job.paused == paused.flag()) return

        job.paused = paused.flag()
        job.nextFireAt = if (paused) null else schedule.nextFire(checkNotNull(job.anchorAt), System.currentTimeMillis())
        jobs.updateById(job)
        writes.record(actor, if (paused) "job.pause" else "job.resume", job.id)
    }

    /** 持久化手动执行请求及任务快照。同一账号可以用原请求标识取回受理结果。 */
    @Transactional
    override fun runNow(id: String, request: RunJobRequest): CreatedResource {
        val actor = writes.authorize(JOB_RUN)
        val runId = request.requestId
        if (!requestIdPattern.matches(runId)) invalid("requestId 须为 32 位小写十六进制字符串")
        runs.selectById(runId)?.let {
            if (it.jobId != id || it.actorId != actor.user.id) conflict("执行请求标识已被使用")
            return CreatedResource(it.id)
        }

        val job = requireJob(id)
        runs.insert(JobRunRecord(
            id = runId,
            jobId = job.id,
            jobName = job.name,
            handler = job.handler,
            actorId = actor.user.id,
            source = JobRunSource.MANUAL,
            requestedAt = System.currentTimeMillis(),
        ))
        writes.record(actor, "job.run", job.id)
        return CreatedResource(runId)
    }

    private fun requireJob(id: String): ScheduledJobRecord = jobs.selectById(id) ?: missing("定时任务")

    private fun applyDefinition(job: ScheduledJobRecord, request: SaveJobRequest) {
        handlers.validate(request.handler)
        job.name = checkedText(request.name, "任务名称")
        job.handler = request.handler
        val schedule = request.schedule
        if (schedule == schedules.read(job)) return

        if (schedule == null) {
            job.scheduleJson = null
            job.anchorAt = null
            job.nextFireAt = null
            job.paused = 0
            return
        }
        val now = System.currentTimeMillis()
        val first = schedule.nextFire(now, now) ?: invalid("触发器没有未来执行时间")
        job.scheduleJson = schedules.write(schedule)
        job.anchorAt = now
        job.nextFireAt = first.takeIf { job.paused == 0 }
    }
}
