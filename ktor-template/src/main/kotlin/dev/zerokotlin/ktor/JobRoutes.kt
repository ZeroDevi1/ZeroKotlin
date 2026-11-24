package dev.zerokotlin.ktor

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.ResultSet

private const val jobs = "system:job:"
private val handlers = listOf(JobHandlerEntry("heartbeat", "调度心跳"), JobHandlerEntry("purge-expired-sessions", "清理过期会话"))
private val requestIdPattern = Regex("[0-9a-f]{32}")
private val scheduleJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

private data class StoredJob(val entry: JobEntry, val anchor: Long?)
private data class Work(val id: String, val fireAt: Long, val manual: Boolean)

internal fun Route.jobRoutes(store: SystemStore) = route("jobs") {
    get {
        val window = call.window()
        call.respond(store.read(call.bearer(), jobs + "read") { db, _ ->
            val data = db.rows("SELECT * FROM sys_scheduled_job ORDER BY name, id LIMIT ? OFFSET ?", window.size, window.offset, map = ::storedJob)
            val total = db.rows("SELECT COUNT(*) FROM sys_scheduled_job") { it.getLong(1) }.single()
            window.result(data.map { it.entry }, total)
        })
    }
    get("handlers") {
        call.respond(store.read(call.bearer(), jobs + "read") { _, _ -> handlers })
    }
    get("logs") {
        val job = call.request.queryParameters["jobId"]?.also { if (it.length !in 1..36) invalid("任务 ID 无效") }
        val window = call.window()
        val where = if (job == null) "" else " WHERE job_id = ?"
        val args = listOfNotNull(job).toTypedArray()
        call.respond(store.read(call.bearer(), jobs + "log:read") { db, _ ->
            val data = db.rows(
                "SELECT * FROM sys_job_run$where ORDER BY requested_at DESC, id DESC LIMIT ? OFFSET ?", *args, window.size, window.offset,
            ) {
                JobRunEntry(
                    it.getString("id"), it.getString("job_id"), it.getString("job_name"), it.getString("handler"),
                    JobRunSource.valueOf(it.getString("source")), JobRunStatus.valueOf(it.getString("status")),
                    it.getLong("requested_at"), it.number("started_at"), it.number("finished_at"), it.getString("message"),
                )
            }
            window.result(data, db.rows("SELECT COUNT(*) FROM sys_job_run$where", *args) { it.getLong(1) }.single())
        })
    }
    post {
        val token = call.bearer()
        val request = call.receive<SaveJobRequest>()
        val id = newId()
        store.write(token, jobs + "create") { db, _, audit ->
            db.saveJob(id, request, existing = null)
            audit("job.create", id)
        }
        call.respond(HttpStatusCode.Created, Created(id))
    }
    route("{id}") {
        put {
            val token = call.bearer()
            val request = call.receive<SaveJobRequest>()
            val id = call.id()
            store.write(token, jobs + "update") { db, _, audit ->
                db.saveJob(id, request, db.requireJob(id))
                audit("job.update", id)
            }
            call.respond(HttpStatusCode.NoContent)
        }
        delete {
            val id = call.id()
            store.write(call.bearer(), jobs + "delete") { db, _, audit ->
                db.requireJob(id)
                db.change(
                    "UPDATE sys_job_run SET status = 'CANCELLED', finished_at = ?, message = ? WHERE job_id = ? AND status = 'QUEUED'",
                    System.currentTimeMillis(), "任务已删除", id,
                )
                db.change("DELETE FROM sys_scheduled_job WHERE id = ?", id)
                audit("job.delete", id)
            }
            call.respond(HttpStatusCode.NoContent)
        }
        post("pause") {
            setPaused(store, call.bearer(), call.id(), true)
            call.respond(HttpStatusCode.NoContent)
        }
        post("resume") {
            setPaused(store, call.bearer(), call.id(), false)
            call.respond(HttpStatusCode.NoContent)
        }
        post("run") {
            val token = call.bearer()
            val id = call.id()
            val request = call.receive<RunJobRequest>()
            if (!requestIdPattern.matches(request.requestId)) invalid("requestId 须为 32 位小写十六进制字符串")
            store.write(token, jobs + "run") { db, actor, audit ->
                val existing = db.rows("SELECT job_id, actor_id FROM sys_job_run WHERE id = ?", request.requestId) {
                    it.getString(1) to it.getString(2)
                }.singleOrNull()
                if (existing != null) {
                    if (existing != (id to actor.id)) throw SystemFailure(409, "执行请求标识已被使用")
                } else {
                    val job = db.requireJob(id).entry
                    db.change(
                        """INSERT INTO sys_job_run (id, job_id, job_name, handler, actor_id, source, status, requested_at)
                           VALUES (?, ?, ?, ?, ?, 'MANUAL', 'QUEUED', ?)""",
                        request.requestId, id, job.name, job.handler, actor.id, System.currentTimeMillis(),
                    )
                    audit("job.run", id)
                }
            }
            call.respond(HttpStatusCode.Accepted, Created(request.requestId))
        }
    }
}

private fun ResultSet.number(column: String): Long? = getLong(column).takeUnless { wasNull() }

private fun storedJob(row: ResultSet) = StoredJob(
    JobEntry(
        row.getString("id"), row.getString("name"), row.getString("handler"),
        row.getString("schedule_json")?.let { scheduleJson.decodeFromString<JobSchedule>(it) },
        row.getInt("paused") == 1, row.number("next_fire_at"),
    ), row.number("anchor_at"),
)

private fun Connection.findJob(id: String) = rows("SELECT * FROM sys_scheduled_job WHERE id = ?", id, map = ::storedJob).singleOrNull()
private fun Connection.requireJob(id: String) = findJob(id) ?: missing("定时任务")

private fun Connection.saveJob(id: String, request: SaveJobRequest, existing: StoredJob?) {
    if (handlers.none { it.key == request.handler }) invalid("未知的任务实现")
    val name = checkedName(request.name)
    if (existing != null && request.schedule == existing.entry.schedule) {
        change("UPDATE sys_scheduled_job SET name = ?, handler = ? WHERE id = ?", name, request.handler, id)
        return
    }
    val trigger = request.schedule
    val anchor = trigger?.let { System.currentTimeMillis() }
    val next = trigger?.let { it.nextFire(checkNotNull(anchor), anchor) ?: invalid("触发器没有未来执行时间") }
    val paused = trigger != null && existing?.entry?.paused == true
    val serialized = trigger?.let { scheduleJson.encodeToString(it) }
    if (existing == null) change(
        """INSERT INTO sys_scheduled_job (id, name, handler, schedule_json, anchor_at, next_fire_at, paused)
           VALUES (?, ?, ?, ?, ?, ?, ?)""",
        id, name, request.handler, serialized, anchor, next, paused.flag(),
    ) else change(
        """UPDATE sys_scheduled_job SET name = ?, handler = ?, schedule_json = ?, anchor_at = ?, next_fire_at = ?, paused = ?
           WHERE id = ?""",
        name, request.handler, serialized, anchor, next.takeUnless { paused }, paused.flag(), id,
    )
}

private suspend fun setPaused(store: SystemStore, token: String, id: String, paused: Boolean) {
    store.write(token, jobs + if (paused) "pause" else "resume") { db, _, audit ->
        val job = db.requireJob(id)
        val schedule = job.entry.schedule ?: throw SystemFailure(409, "任务未配置自动触发器")
        if (job.entry.paused != paused) {
            val next = if (paused) null else schedule.nextFire(checkNotNull(job.anchor), System.currentTimeMillis())
            db.change("UPDATE sys_scheduled_job SET paused = ?, next_fire_at = ? WHERE id = ?", paused.flag(), next, id)
            audit(if (paused) "job.pause" else "job.resume", id)
        }
    }
}

/** 由应用生命周期管理；Quartz 只计算触发时间，数据库锁保证同一到期记录只消费一次。 */
internal fun Application.startJobWorker(store: SystemStore) {
    launch(Dispatchers.IO) {
        while (isActive) {
            try {
                val now = System.currentTimeMillis()
                val pending = store.transaction { db ->
                    db.rows("SELECT id, next_fire_at FROM sys_scheduled_job WHERE next_fire_at <= ? ORDER BY next_fire_at, id LIMIT 16", now) {
                        Work(it.getString(1), it.getLong(2), false)
                    } + db.rows("SELECT id, requested_at FROM sys_job_run WHERE status = 'QUEUED' ORDER BY requested_at, id LIMIT 16") {
                        Work(it.getString(1), it.getLong(2), true)
                    }
                }
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

private fun Connection.performJob(item: Work) {
    val started = System.currentTimeMillis()
    if (item.manual) {
        val handler = rows("SELECT handler FROM sys_job_run WHERE id = ? AND status = 'QUEUED'", item.id) { it.getString(1) }
            .singleOrNull() ?: return
        val (status, message) = jobEffect(handler, started)
        change(
            "UPDATE sys_job_run SET status = ?, started_at = ?, finished_at = ?, message = ? WHERE id = ? AND status = 'QUEUED'",
            status, started, maxOf(started, System.currentTimeMillis()), message, item.id,
        )
        return
    }
    val job = findJob(item.id) ?: return
    // 持有写锁时复核扫描快照：暂停、改期或已被另一实例处理则跳过。
    if (job.entry.paused || job.entry.nextFireAt != item.fireAt || item.fireAt > started) return
    val (status, message) = jobEffect(job.entry.handler, started)
    val finished = maxOf(started, System.currentTimeMillis())
    val trigger = checkNotNull(job.entry.schedule)
    val next = trigger.nextFire(checkNotNull(job.anchor), finished)
    change("UPDATE sys_scheduled_job SET next_fire_at = ? WHERE id = ?", next, item.id)
    change(
        """INSERT INTO sys_job_run
           (id, job_id, job_name, handler, source, status, requested_at, started_at, finished_at, message)
           VALUES (?, ?, ?, ?, 'SCHEDULED', ?, ?, ?, ?, ?)""",
        newId(), item.id, job.entry.name, job.entry.handler, status, item.fireAt, started, finished, message,
    )
}

private fun Connection.jobEffect(handler: String, now: Long): Pair<String, String> = when (handler) {
    "heartbeat" -> "SUCCESS" to "调度心跳执行成功"
    "purge-expired-sessions" -> "SUCCESS" to "已清理 ${change("DELETE FROM sys_session WHERE expires_at <= ?", now)} 条过期会话"
    else -> "FAILED" to "本地任务实现已移除"
}
