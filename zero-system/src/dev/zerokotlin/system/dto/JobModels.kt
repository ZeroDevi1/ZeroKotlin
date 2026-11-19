package dev.zerokotlin.system.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.quartz.DateBuilder.IntervalUnit

/** 固定间隔和日内间隔可选的时钟单位。 */
enum class JobClockUnit(val quartz: IntervalUnit, val seconds: Long) {
    SECONDS(IntervalUnit.SECOND, 1),
    MINUTES(IntervalUnit.MINUTE, 60),
    HOURS(IntervalUnit.HOUR, 3_600),
}

/** 按指定时区计算的日历单位。 */
enum class JobCalendarUnit(val quartz: IntervalUnit, val maximum: Int) {
    DAYS(IntervalUnit.DAY, 366),
    WEEKS(IntervalUnit.WEEK, 52),
    MONTHS(IntervalUnit.MONTH, 12),
    YEARS(IntervalUnit.YEAR, 10),
}

/** Quartz Cron 表达式及其解释时区。 */
data class JobCron(
    @field:Schema(description = "Quartz 的 6 或 7 字段 Cron 表达式。", example = "0 0 9 ? * MON-FRI")
    val expression: String,
    @field:Schema(description = "Java ZoneId 支持的时区 ID，默认 UTC。", maxLength = 64, example = "Asia/Shanghai")
    val timeZone: String = "UTC",
)

/** 以实际经过时间计的固定间隔与可选重复次数。 */
data class JobSimple(
    @field:Schema(description = "间隔数值。", minimum = "1", maximum = "86400")
    val interval: Int,
    val unit: JobClockUnit,
    @field:Schema(description = "首次触发后的重复次数。0 表示仅一次，空值表示无限重复。", minimum = "0", maximum = "1000000")
    val repeatCount: Int? = null,
)

/** 在指定时区内按日、周、月或年计算的间隔。 */
data class JobCalendar(
    @field:Schema(description = "间隔数值，日、周、月、年的上限分别为 366、52、12、10。", minimum = "1")
    val interval: Int,
    val unit: JobCalendarUnit,
    @field:Schema(description = "Java ZoneId 支持的时区 ID，默认 UTC。", maxLength = 64, example = "Asia/Shanghai")
    val timeZone: String = "UTC",
)

/** 在指定星期及服务器时区的日内时间窗口中重复触发。 */
data class JobDaily(
    @field:Schema(description = "ISO 星期编号，1 为周一，7 为周日。")
    val daysOfWeek: Set<Int>,
    @field:Schema(description = "窗口开始时间，格式 HH:mm。", example = "09:00")
    val startTime: String,
    @field:Schema(description = "窗口结束时间，格式 HH:mm，须晚于开始时间。", example = "18:00")
    val endTime: String,
    @field:Schema(description = "间隔数值，换算后最多为 24 小时。", minimum = "1")
    val interval: Int,
    val unit: JobClockUnit,
)

/** 一个自动触发计划。四种触发器配置须恰好提供一项。 */
@Schema(description = "cron、simple、calendar、daily 中须恰好提供一项。")
data class JobSchedule(
    val cron: JobCron? = null,
    val simple: JobSimple? = null,
    val calendar: JobCalendar? = null,
    val daily: JobDaily? = null,
)

/** 新建和编辑任务使用的定义。 */
data class SaveJobRequest(
    @field:Schema(description = "任务名称。", maxLength = 120)
    val name: String,
    @field:Schema(description = "本地允许名单中的任务实现键。", example = "heartbeat")
    val handler: String,
    @field:Schema(description = "自动触发计划，空值表示仅手动执行。")
    val schedule: JobSchedule? = null,
)

/** 已持久化的任务定义与下一次待执行的计划时间。 */
data class JobEntry(
    val id: String,
    val name: String,
    val handler: String,
    val schedule: JobSchedule?,
    val paused: Boolean,
    @field:Schema(description = "下一次待执行的计划时间，Unix 毫秒，暂停或无未来计划时为空。")
    val nextFireAt: Long?,
)

/** 可供管理端选择的本地任务实现。 */
data class JobHandlerEntry(val key: String, val name: String)

/** 用于安全重试手动执行请求的调用方生成标识。 */
data class RunJobRequest(
    @field:Schema(description = "32 位小写十六进制请求标识。同一用户重试同一执行请求时使用同一标识。")
    val requestId: String,
)

/** 日志中记录的触发来源。 */
enum class JobRunSource { SCHEDULED, MANUAL }

/** 执行结果，手动请求在等待工作线程时处于 QUEUED。 */
enum class JobRunStatus { QUEUED, SUCCESS, FAILED, CANCELLED }

/** 任务日志保存提交时的任务名称与实现键，任务删除后仍可查看。 */
data class JobRunEntry(
    val id: String,
    val jobId: String,
    val jobName: String,
    val handler: String,
    val source: JobRunSource,
    val status: JobRunStatus,
    @field:Schema(description = "计划触发时间或手动请求提交时间，Unix 毫秒。")
    val requestedAt: Long,
    @field:Schema(description = "开始执行时间，Unix 毫秒。等待或取消时为空。")
    val startedAt: Long?,
    @field:Schema(description = "执行结束或取消时间，Unix 毫秒。等待时为空。")
    val finishedAt: Long?,
    val message: String?,
)
