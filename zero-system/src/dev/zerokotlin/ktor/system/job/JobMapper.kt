package dev.zerokotlin.ktor.system.job

import dev.zerokotlin.ktor.Page
import dev.zerokotlin.ktor.Window
import dev.zerokotlin.ktor.change
import dev.zerokotlin.ktor.checkedName
import dev.zerokotlin.ktor.flag
import dev.zerokotlin.ktor.invalid
import dev.zerokotlin.ktor.missing
import dev.zerokotlin.ktor.newId
import dev.zerokotlin.ktor.rows
import java.sql.Connection
import java.sql.ResultSet
import kotlinx.serialization.json.Json

private val scheduleJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

internal data class StoredJob(val entry: JobEntry, val anchor: Long?)
internal data class Work(val id: String, val fireAt: Long, val manual: Boolean)

/** 调度 SQL 显式持久化触发器快照、工作项状态和幂等键，供管理服务与后台协程共用。 */
internal fun Connection.jobPage(window: Window): Page<JobEntry> {
    val data = rows("SELECT * FROM sys_scheduled_job ORDER BY name, id LIMIT ? OFFSET ?", window.size, window.offset, map = ::storedJob)
    val total = rows("SELECT COUNT(*) FROM sys_scheduled_job") { it.getLong(1) }.single()
    return window.result(data.map { it.entry }, total)
}

internal fun Connection.jobLogPage(window: Window, job: String?): Page<JobRunEntry> {
    val where = if (job == null) "" else " WHERE job_id = ?"
    val args = listOfNotNull(job).toTypedArray()
    val data = rows(
        "SELECT * FROM sys_job_run$where ORDER BY requested_at DESC, id DESC LIMIT ? OFFSET ?",
        *args, window.size, window.offset,
    ) {
        JobRunEntry(
            it.getString("id"), it.getString("job_id"), it.getString("job_name"), it.getString("handler"),
            JobRunSource.valueOf(it.getString("source")), JobRunStatus.valueOf(it.getString("status")),
            it.getLong("requested_at"), it.number("started_at"), it.number("finished_at"), it.getString("message"),
        )
    }
    return window.result(data, rows("SELECT COUNT(*) FROM sys_job_run$where", *args) { it.getLong(1) }.single())
}

internal fun Connection.deleteJob(id: String) {
    change(
        "UPDATE sys_job_run SET status = 'CANCELLED', finished_at = ?, message = ? WHERE job_id = ? AND status = 'QUEUED'",
        System.currentTimeMillis(), "任务已删除", id,
    )
    change("DELETE FROM sys_scheduled_job WHERE id = ?", id)
}

internal fun Connection.pauseJob(id: String, paused: Boolean, next: Long?) {
    change("UPDATE sys_scheduled_job SET paused = ?, next_fire_at = ? WHERE id = ?", paused.flag(), next, id)
}

internal fun Connection.jobRunOwner(id: String): Pair<String, String?>? =
    rows("SELECT job_id, actor_id FROM sys_job_run WHERE id = ?", id) { it.getString(1) to it.getString(2) }.singleOrNull()

internal fun Connection.enqueueJob(job: JobEntry, user: String, requestId: String) {
    change(
        """INSERT INTO sys_job_run (id, job_id, job_name, handler, actor_id, source, status, requested_at)
           VALUES (?, ?, ?, ?, ?, 'MANUAL', 'QUEUED', ?)""",
        requestId, job.id, job.name, job.handler, user, System.currentTimeMillis(),
    )
}

internal fun Connection.pendingJobs(now: Long): List<Work> =
    rows("SELECT id, next_fire_at FROM sys_scheduled_job WHERE next_fire_at <= ? ORDER BY next_fire_at, id LIMIT 16", now) {
        Work(it.getString(1), it.getLong(2), false)
    } + rows("SELECT id, requested_at FROM sys_job_run WHERE status = 'QUEUED' ORDER BY requested_at, id LIMIT 16") {
        Work(it.getString(1), it.getLong(2), true)
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
internal fun Connection.requireJob(id: String) = findJob(id) ?: missing("定时任务")

internal fun Connection.saveJob(id: String, request: SaveJobRequest, existing: StoredJob?) {
    if (jobHandlers.none { it.key == request.handler }) invalid("未知的任务实现")
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

internal fun Connection.performJob(item: Work) {
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
