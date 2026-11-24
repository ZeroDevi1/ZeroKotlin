package dev.zerokotlin.ktor.system.audit

import dev.zerokotlin.ktor.Page
import dev.zerokotlin.ktor.Window
import dev.zerokotlin.ktor.change
import dev.zerokotlin.ktor.flag
import dev.zerokotlin.ktor.newId
import dev.zerokotlin.ktor.rows
import java.sql.Connection
import java.sql.ResultSet
import kotlinx.serialization.Serializable

@Serializable internal data class LoginAudit(
    val id: String, val userId: String?, val username: String?, val successful: Boolean,
    val remoteAddress: String, val occurredAt: Long, val ipRegion: String?,
)
@Serializable internal data class OperationAudit(
    val id: String, val actorId: String, val action: String, val targetId: String, val occurredAt: Long,
)
@Serializable internal data class RequestAudit(
    val id: String, val actorId: String?, val action: String, val httpMethod: String, val routePattern: String,
    val httpStatus: Int, val durationMs: Long, val exceptionType: String?, val remoteAddress: String,
    val ipRegion: String?, val userAgent: String?, val occurredAt: Long,
)

/** 审计表及列名固定在 Mapper 内，筛选值使用 JDBC 参数绑定。 */
internal fun Connection.loginAuditPage(window: Window, successful: Boolean?): Page<LoginAudit> {
    val where = if (successful == null) "" else " WHERE successful = ?"
    return auditPage(window, "sys_login_audit", where, listOfNotNull(successful?.flag())) {
        LoginAudit(
            it.getString("id"), it.getString("user_id"), it.getString("username"), it.getInt("successful") == 1,
            it.getString("remote_address"), it.getLong("occurred_at"), it.getString("ip_region"),
        )
    }
}

internal fun Connection.operationAuditPage(window: Window, actorId: String?, action: String?): Page<OperationAudit> {
    val filters = listOfNotNull(actorId?.let { "actor_id = ?" to it }, action?.let { "action = ?" to it })
    val where = if (filters.isEmpty()) "" else filters.joinToString(" AND ", prefix = " WHERE ") { it.first }
    return auditPage(window, "sys_operation_audit", where, filters.map { it.second }) {
        OperationAudit(it.getString("id"), it.getString("actor_id"), it.getString("action"), it.getString("target_id"), it.getLong("occurred_at"))
    }
}

internal fun Connection.requestAuditPage(window: Window, successful: Boolean?): Page<RequestAudit> {
    val where = when (successful) { true -> " WHERE http_status < 400"; false -> " WHERE http_status >= 400"; null -> "" }
    return auditPage(window, "sys_request_audit", where, emptyList()) {
        RequestAudit(
            it.getString("id"), it.getString("actor_id"), it.getString("action"), it.getString("http_method"),
            it.getString("route_pattern"), it.getInt("http_status"), it.getLong("duration_ms"),
            it.getString("exception_type"), it.getString("remote_address"), it.getString("ip_region"),
            it.getString("user_agent"), it.getLong("occurred_at"),
        )
    }
}

/** 监控请求审计独立保存，调用方负责处理写入失败。 */
internal fun Connection.recordMonitorRequest(actorId: String?, status: Int, tookMs: Long, exception: String?, address: String) {
    change(
        """INSERT INTO sys_request_audit
           (id, actor_id, action, http_method, route_pattern, http_status, duration_ms, exception_type, remote_address, occurred_at)
           VALUES (?, ?, 'monitor.read', 'GET', '/api/system/monitor', ?, ?, ?, ?, ?)""",
        newId(), actorId, status, tookMs, exception, address.take(128), System.currentTimeMillis(),
    )
}

private fun <T> Connection.auditPage(window: Window, table: String, where: String, filters: List<Any>, map: (ResultSet) -> T): Page<T> {
    val args = filters.toTypedArray()
    val count = rows("SELECT COUNT(*) FROM $table$where", *args) { it.getLong(1) }.single()
    val records = rows("SELECT * FROM $table$where ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?",
        *args, window.size, window.offset, map = map)
    return window.result(records, count)
}
