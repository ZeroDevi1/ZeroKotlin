package dev.zerokotlin.core.config

import java.util.Properties
import java.util.UUID
import org.springframework.beans.factory.BeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.quartz.SchedulerFactoryBean

/** 提供进程内 Quartz 调度器。业务模块负责恢复持久状态，并在数据库迁移后启动调度。 */
@Configuration(proxyBeanMethods = false)
class QuartzConfiguration {
    /** 由 Spring 提供 Quartz 作业实例，并在关闭应用时等待正在执行的作业结束。 */
    @Bean
    fun zeroQuartzScheduler(beans: BeanFactory): SchedulerFactoryBean {
        return SchedulerFactoryBean().apply {
            setSchedulerName("zero-quartz-${UUID.randomUUID()}")
            setQuartzProperties(Properties().apply { setProperty("org.quartz.threadPool.threadCount", "2") })
            setAutoStartup(false)
            setWaitForJobsToCompleteOnShutdown(true)
            setJobFactory { bundle, _ -> beans.getBean(bundle.jobDetail.jobClass) }
        }
    }
}
