package dev.zerokotlin.system.service

import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import dev.zerokotlin.system.dto.JobRunSource
import dev.zerokotlin.system.dto.JobRunStatus
import dev.zerokotlin.system.entity.JobRunRecord
import dev.zerokotlin.system.entity.ScheduledJobRecord
import dev.zerokotlin.system.mapper.JobRunMapper
import dev.zerokotlin.system.mapper.ScheduledJobMapper
import dev.zerokotlin.system.support.newId
import dev.zerokotlin.system.support.query
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.RecoverableDataAccessException
import org.springframework.dao.TransientDataAccessException
import org.springframework.stereotype.Service
import org.springframework.transaction.TransactionException
import org.springframework.transaction.annotation.Transactional

/** Quartz 派发的持久化工作项。自动计划以任务 ID 和到期时间标识，手动请求以运行 ID 标识。 */
data class JobDispatch(val id: String, val fireAt: Long, val manual: Boolean)

/** 从数据库恢复待执行项，并在一个写入事务内写入执行结果、任务效果和调度进度。 */
@Service
class SystemJobExecutions(
    private val jobs: ScheduledJobMapper,
    private val runs: JobRunMapper,
    private val schedules: JobScheduleStorage,
    private val handlers: JobHandlers,
    private val writes: SystemWrites,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** 每批最多取 16 条自动计划和 16 条手动请求，交由 Quartz 触发。 */
    @Transactional(readOnly = true)
    fun pending(before: Long): List<JobDispatch> {
        val scheduled = jobs.selectPage(
            Page<ScheduledJobRecord>(1, 16, false),
            query<ScheduledJobRecord>().le("next_fire_at", before).orderByAsc("next_fire_at", "id"),
        )
        val manual = runs.selectPage(
            Page<JobRunRecord>(1, 16, false),
            query<JobRunRecord>().eq("status", JobRunStatus.QUEUED.name).orderByAsc("requested_at", "id"),
        )
        return scheduled.records.map { JobDispatch(it.id, checkNotNull(it.nextFireAt), false) } +
            manual.records.map { JobDispatch(it.id, it.requestedAt, true) }
    }

    /** 在写入锁下重新核对 Quartz 触发项，已消费、暂停或删除的工作项直接结束。 */
    @Transactional
    fun execute(dispatch: JobDispatch) {
        writes.lock()
        val now = System.currentTimeMillis()
        if (dispatch.manual) {
            val run = runs.selectById(dispatch.id) ?: return
            if (run.status != JobRunStatus.QUEUED) return
            perform(run, now)
            runs.updateById(run)
            return
        }

        val job = jobs.selectById(dispatch.id) ?: return
        if (job.paused != 0 || job.nextFireAt != dispatch.fireAt || dispatch.fireAt > now) return
        val schedule = checkNotNull(schedules.read(job))
        val run = JobRunRecord(
            id = newId(), jobId = job.id, jobName = job.name, handler = job.handler,
            source = JobRunSource.SCHEDULED, requestedAt = dispatch.fireAt,
        )
        perform(run, now)
        job.nextFireAt = schedule.nextFire(checkNotNull(job.anchorAt), checkNotNull(run.finishedAt))
        jobs.updateById(job)
        runs.insert(run)
    }

    private fun perform(run: JobRunRecord, now: Long) {
        run.startedAt = now
        try {
            run.message = handlers.execute(run.handler, now).take(500)
            run.status = JobRunStatus.SUCCESS
        } catch (failure: RuntimeException) {
            // 基础设施故障交由外层事务回滚，下次派发继续使用同一个持久化工作项。
            if (failure is DataAccessResourceFailureException ||
                failure is TransientDataAccessException || failure is RecoverableDataAccessException ||
                failure is TransactionException
            ) throw failure
            logger.warn("Scheduled job execution failed: job={}, run={}, handler={}", run.jobId, run.id, run.handler, failure)
            run.status = JobRunStatus.FAILED
            run.message = "任务执行失败，详见服务日志"
        }
        run.finishedAt = maxOf(now, System.currentTimeMillis())
    }
}
