package dev.zerokotlin.ktor.system.job

import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.Page
import dev.zerokotlin.ktor.SystemStore
import dev.zerokotlin.ktor.Window
import dev.zerokotlin.ktor.invalid
import dev.zerokotlin.ktor.newId

internal val jobHandlers = listOf(JobHandlerEntry("heartbeat", "调度心跳"), JobHandlerEntry("purge-expired-sessions", "清理过期会话"))

/** 任务设置、手动队列和变更审计在 SystemStore 的事务内维护。 */
internal class JobService(private val store: SystemStore) {
    private val prefix = "system:job:"
    private val requestIdPattern = Regex("[0-9a-f]{32}")

    suspend fun list(token: String, window: Window): Page<JobEntry> =
        store.read(token, prefix + "read") { db, _ -> db.jobPage(window) }

    suspend fun handlers(token: String): List<JobHandlerEntry> =
        store.read(token, prefix + "read") { _, _ -> jobHandlers }

    suspend fun logs(token: String, window: Window, job: String?): Page<JobRunEntry> {
        if (job != null && job.length !in 1..36) invalid("任务 ID 无效")
        return store.read(token, prefix + "log:read") { db, _ -> db.jobLogPage(window, job) }
    }

    suspend fun create(token: String, request: SaveJobRequest): Created {
        val id = newId()
        store.write(token, prefix + "create") { db, _, audit ->
            db.saveJob(id, request, existing = null)
            audit("job.create", id)
        }
        return Created(id)
    }

    suspend fun update(token: String, id: String, request: SaveJobRequest) {
        store.write(token, prefix + "update") { db, _, audit ->
            db.saveJob(id, request, db.requireJob(id))
            audit("job.update", id)
        }
    }

    suspend fun delete(token: String, id: String) {
        store.write(token, prefix + "delete") { db, _, audit ->
            db.requireJob(id)
            db.deleteJob(id)
            audit("job.delete", id)
        }
    }

    suspend fun setPaused(token: String, id: String, paused: Boolean) {
        store.write(token, prefix + if (paused) "pause" else "resume") { db, _, audit ->
            val job = db.requireJob(id)
            val schedule = job.entry.schedule ?: throw SystemFailure(409, "任务未配置自动触发器")
            if (job.entry.paused != paused) {
                val next = if (paused) null else schedule.nextFire(checkNotNull(job.anchor), System.currentTimeMillis())
                db.pauseJob(id, paused, next)
                audit(if (paused) "job.pause" else "job.resume", id)
            }
        }
    }

    /** 同一账号为同一任务重复提交 requestId 时复用已有队列记录。 */
    suspend fun submitRun(token: String, id: String, request: RunJobRequest): Created {
        if (!requestIdPattern.matches(request.requestId)) invalid("requestId 须为 32 位小写十六进制字符串")
        store.write(token, prefix + "run") { db, actor, audit ->
            val existing = db.jobRunOwner(request.requestId)
            if (existing != null) {
                if (existing != (id to actor.id)) throw SystemFailure(409, "执行请求标识已被使用")
            } else {
                db.enqueueJob(db.requireJob(id).entry, actor.id, request.requestId)
                audit("job.run", id)
            }
        }
        return Created(request.requestId)
    }
}
