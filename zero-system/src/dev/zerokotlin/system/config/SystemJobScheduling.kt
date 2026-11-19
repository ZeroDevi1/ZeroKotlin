package dev.zerokotlin.system.config

import dev.zerokotlin.system.service.JobDispatch
import dev.zerokotlin.system.service.SystemJobExecutions
import java.util.Date
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobBuilder
import org.quartz.JobExecutionContext
import org.quartz.JobKey
import org.quartz.ObjectAlreadyExistsException
import org.quartz.Scheduler
import org.quartz.SimpleScheduleBuilder
import org.quartz.TriggerBuilder
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

private const val group = "system-jobs"
private val worker = JobKey.jobKey("worker", group)

/** 应用数据库初始化后启动 Quartz。重启后由回填作业重新读取数据库中的待执行项。 */
@Component
class SystemJobScheduling(private val scheduler: Scheduler) : ApplicationRunner {
    /** 安装工作线程与每秒一次的回填触发器。 */
    override fun run(args: ApplicationArguments) {
        scheduler.addJob(JobBuilder.newJob(SystemJobWorker::class.java).withIdentity(worker).storeDurably().build(), false)
        scheduler.scheduleJob(
            JobBuilder.newJob(SystemJobDispatchPoller::class.java).withIdentity("dispatch", group).build(),
            TriggerBuilder.newTrigger().withIdentity("dispatch", group).startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(1).repeatForever()
                    .withMisfireHandlingInstructionNextWithExistingCount())
                .build(),
        )
        scheduler.start()
    }
}

/** 用独立的一次性 Quartz 触发器派发到期项及两秒内即将到期的计划。 */
@Component
@DisallowConcurrentExecution
class SystemJobDispatchPoller(private val executions: SystemJobExecutions) : Job {
    /** Quartz 中已有同键触发器时保留原触发器，数据库仍负责最终的幂等检查。 */
    override fun execute(context: JobExecutionContext) {
        for (dispatch in executions.pending(System.currentTimeMillis() + 2_000)) {
            val name = if (dispatch.manual) "manual:${dispatch.id}" else "auto:${dispatch.id}:${dispatch.fireAt}"
            val trigger = TriggerBuilder.newTrigger().withIdentity(name, group).forJob(worker)
                .startAt(Date(dispatch.fireAt))
                .usingJobData("id", dispatch.id)
                .usingJobData("fireAt", dispatch.fireAt)
                .usingJobData("manual", dispatch.manual)
                .build()
            try {
                context.scheduler.scheduleJob(trigger)
            } catch (_: ObjectAlreadyExistsException) {
                // 前一批已派发该工作项。
            }
        }
    }
}

/** 执行已由 Quartz 触发的工作项。同一进程内一次只执行一项系统任务。 */
@Component
@DisallowConcurrentExecution
class SystemJobWorker(private val executions: SystemJobExecutions) : Job {
    /** 按持久化标识消费工作项，触发器中的数据仅用于定位记录。 */
    override fun execute(context: JobExecutionContext) {
        val data = context.mergedJobDataMap
        executions.execute(JobDispatch(data.getString("id"), data.getLong("fireAt"), data.getBoolean("manual")))
    }
}
