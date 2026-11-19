package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import dev.zerokotlin.system.dto.JobRunSource
import dev.zerokotlin.system.dto.JobRunStatus

/**
 * 手动请求与运行结果共用的记录。任务快照允许删除任务后继续读取执行历史。
 *
 * @property id 单次执行记录的主键。
 * @property jobId 任务定义的 ID，删除任务后仍保留。
 * @property jobName 生成执行记录时的任务名称。
 * @property handler 生成执行记录时的处理器编码。
 * @property actorId 手动触发的用户 ID，自动调度时为空。
 * @property source 触发来源，区分手动请求和自动调度。
 * @property status 排队、取消或运行完成后的状态。
 * @property requestedAt 手动请求或计划触发的 Unix 毫秒时间戳。
 * @property startedAt 开始执行的 Unix 毫秒时间戳，尚未开始时为空。
 * @property finishedAt 执行结束或取消的 Unix 毫秒时间戳，排队时为空。
 * @property message 可选的执行结果或取消原因。
 */
@TableName("sys_job_run")
class JobRunRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    var jobId: String = "",
    var jobName: String = "",
    var handler: String = "",
    var actorId: String? = null,
    var source: JobRunSource = JobRunSource.MANUAL,
    var status: JobRunStatus = JobRunStatus.QUEUED,
    var requestedAt: Long = 0,
    var startedAt: Long? = null,
    var finishedAt: Long? = null,
    var message: String? = null,
)
