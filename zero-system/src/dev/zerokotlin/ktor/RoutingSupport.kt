package dev.zerokotlin.ktor

import dev.zerokotlin.core.SystemFailure
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header

internal fun ApplicationCall.noStore() = response.header(HttpHeaders.CacheControl, "no-store")

/** 读取唯一的 Bearer 头并关闭响应缓存，会话有效性由事务入口复核。 */
internal fun ApplicationCall.bearer(): String {
    noStore()
    val header = request.headers.getAll(HttpHeaders.Authorization)?.singleOrNull()
    if (header == null || !header.startsWith("Bearer ", ignoreCase = true)) throw SystemFailure(401, "登录已失效")
    return header.substring(7)
}

internal fun ApplicationCall.window(): Window {
    val query = request.queryParameters
    val page = query["page"]?.let { it.toIntOrNull() ?: 0 } ?: 1
    val size = query["size"]?.let { it.toIntOrNull() ?: 0 } ?: 20
    if (page < 1 || size !in 1..100) throw SystemFailure(400, "page 必须大于等于 1，size 必须介于 1 和 100 之间")
    return Window(page, size)
}

internal fun ApplicationCall.id() = parameters["id"] ?: throw SystemFailure(400, "缺少资源 ID")
