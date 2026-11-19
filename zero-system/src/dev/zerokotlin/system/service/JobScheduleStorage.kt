package dev.zerokotlin.system.service

import dev.zerokotlin.system.dto.JobSchedule
import dev.zerokotlin.system.entity.ScheduledJobRecord
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/** 使用应用的 JSON 映射器读写持久化的 Quartz 触发器配置。 */
@Component
@RegisterReflectionForBinding(JobSchedule::class)
class JobScheduleStorage(private val json: JsonMapper) {
    /** 读取任务的自动计划。 */
    fun read(job: ScheduledJobRecord): JobSchedule? =
        job.scheduleJson?.let { json.readValue(it, JobSchedule::class.java) }

    /** 序列化已经过校验的自动计划。 */
    fun write(schedule: JobSchedule): String = json.writeValueAsString(schedule)
}
