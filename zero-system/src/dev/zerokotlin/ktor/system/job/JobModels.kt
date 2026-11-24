package dev.zerokotlin.ktor.system.job

import kotlinx.serialization.Serializable
import org.quartz.DateBuilder.IntervalUnit

/** 固定间隔和日内间隔可选的时钟单位。 */
@Serializable
internal enum class JobClockUnit(val quartz: IntervalUnit, val seconds: Long) {
    SECONDS(IntervalUnit.SECOND, 1),
    MINUTES(IntervalUnit.MINUTE, 60),
    HOURS(IntervalUnit.HOUR, 3_600),
}

/** 按指定时区计算的日历单位。 */
@Serializable
internal enum class JobCalendarUnit(val quartz: IntervalUnit, val maximum: Int) {
    DAYS(IntervalUnit.DAY, 366),
    WEEKS(IntervalUnit.WEEK, 52),
    MONTHS(IntervalUnit.MONTH, 12),
    YEARS(IntervalUnit.YEAR, 10),
}

/** Quartz Cron 表达式及其解释时区。 */
@Serializable
internal data class JobCron(
    val expression: String,
    val timeZone: String = "UTC",
)

/** 以实际经过时间计的固定间隔与可选重复次数。 */
@Serializable
internal data class JobSimple(
    val interval: Int,
    val unit: JobClockUnit,
    val repeatCount: Int? = null,
)

/** 在指定时区内按日、周、月或年计算的间隔。 */
@Serializable
internal data class JobCalendar(
    val interval: Int,
    val unit: JobCalendarUnit,
    val timeZone: String = "UTC",
)

/** 在指定星期及服务器时区的日内时间窗口中重复触发。 */
@Serializable
internal data class JobDaily(
    val daysOfWeek: Set<Int>,
    val startTime: String,
    val endTime: String,
    val interval: Int,
    val unit: JobClockUnit,
)

/** 一个自动触发计划。四种触发器配置须恰好提供一项。 */
@Serializable
internal data class JobSchedule(
    val cron: JobCron? = null,
    val simple: JobSimple? = null,
    val calendar: JobCalendar? = null,
    val daily: JobDaily? = null,
)

/** 新建和编辑任务使用的定义。 */
@Serializable
internal data class SaveJobRequest(
    val name: String,
    val handler: String,
    val schedule: JobSchedule? = null,
)

/** 已持久化的任务定义与下一次待执行的计划时间。 */
@Serializable
internal data class JobEntry(
    val id: String,
    val name: String,
    val handler: String,
    val schedule: JobSchedule?,
    val paused: Boolean,
    val nextFireAt: Long?,
)

/** 可供管理端选择的本地任务实现。 */
@Serializable
internal data class JobHandlerEntry(val key: String, val name: String)

/** 用于安全重试手动执行请求的调用方生成标识。 */
@Serializable
internal data class RunJobRequest(
    val requestId: String,
)

/** 日志中记录的触发来源。 */
@Serializable
internal enum class JobRunSource { SCHEDULED, MANUAL }

/** 执行结果，手动请求在等待工作线程时处于 QUEUED。 */
@Serializable
internal enum class JobRunStatus { QUEUED, SUCCESS, FAILED, CANCELLED }

/** 任务日志保存提交时的任务名称与实现键，任务删除后仍可查看。 */
@Serializable
internal data class JobRunEntry(
    val id: String,
    val jobId: String,
    val jobName: String,
    val handler: String,
    val source: JobRunSource,
    val status: JobRunStatus,
    val requestedAt: Long,
    val startedAt: Long?,
    val finishedAt: Long?,
    val message: String?,
)
