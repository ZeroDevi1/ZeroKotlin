package dev.zerokotlin.ktor.system.job

import dev.zerokotlin.ktor.SystemStore
import io.ktor.server.application.Application
import io.ktor.server.application.log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 由应用生命周期管理；Quartz 只计算触发时间，数据库锁保证同一到期记录只消费一次。 */
internal fun Application.startJobWorker(store: SystemStore) {
    launch(Dispatchers.IO) {
        while (isActive) {
            try {
                val now = System.currentTimeMillis()
                val pending = store.transaction { it.pendingJobs(now) }
                for (item in pending) store.transaction(write = true) { db -> db.performJob(item) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                log.warn("任务调度暂不可用（{}）", failure.javaClass.simpleName)
            }
            delay(1_000)
        }
    }
}
