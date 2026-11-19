package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import dev.zerokotlin.system.dto.JobRunSource
import dev.zerokotlin.system.dto.JobRunStatus

/** 手动请求与运行结果共用的记录。任务快照允许删除任务后继续读取执行历史。 */
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
