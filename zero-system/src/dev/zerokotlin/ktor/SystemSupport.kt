package dev.zerokotlin.ktor

import dev.zerokotlin.core.SystemFailure
import java.sql.Connection
import java.util.UUID
import kotlinx.serialization.Serializable

internal val implementedMenuPaths = setOf("/permissions", "/roles", "/menus", "/departments", "/users", "/positions", "/dictionaries", "/announcements", "/audits", "/operations", "/files", "/jobs", "/realtime")

internal const val administratorRole = "system-admin"

internal const val userRead = "system:user:read"

@Serializable internal data class Created(val id: String)
@Serializable internal data class DeleteBatch(val ids: List<String?>)

/** 分页窗口统一使用 1 起始页码，按 Long 计算偏移以避免溢出。 */
internal data class Window(val page: Int, val size: Int) {
    val offset = (page.toLong() - 1) * size
    fun <T> result(items: List<T>, total: Long) = Page(items, total, page, size)
}

internal fun newId() = UUID.randomUUID().toString()
internal fun Boolean.flag() = if (this) 1 else 0
internal fun invalid(message: String): Nothing = throw SystemFailure(400, message)
internal fun missing(name: String): Nothing = throw SystemFailure(404, "${name}不存在")
internal fun exists(db: Connection, sql: String, vararg values: Any?) = db.rows(sql, *values) { it.getInt(1) }.isNotEmpty()

/** 批量操作统一约束提交数量，重复 ID 只处理一次。 */
internal fun checkedDeleteIds(ids: List<String?>): Set<String> {
    if (ids.size !in 1..100) invalid("每次须指定 1 至 100 个待删除 ID")
    return ids.map { it?.takeIf { id -> id.isNotBlank() && id.length <= 36 } ?: invalid("资源 ID 无效") }.toSet()
}

internal val permissionPattern = Regex("[a-z0-9][a-z0-9:_-]{0,119}")
internal val scopeOrder = listOf("SELF", "DEPARTMENT", "DEPARTMENT_AND_DESCENDANTS", "ALL")
