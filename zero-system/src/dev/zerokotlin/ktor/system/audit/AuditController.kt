package dev.zerokotlin.ktor.system.audit

import dev.zerokotlin.ktor.bearer
import dev.zerokotlin.ktor.invalid
import dev.zerokotlin.ktor.window
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/** /api/system/audits。Controller 解析分页和筛选，由 AuditService 校验读取权限。 */
internal fun Route.auditController(service: AuditService) = route("audits") {
    get("logins") {
        val window = call.window()
        val successful = call.request.queryParameters["successful"]?.let { it.toBooleanStrictOrNull() ?: invalid("successful 必须为布尔值") }
        call.respond(service.logins(call.bearer(), window, successful))
    }
    get("operations") {
        val window = call.window()
        val query = call.request.queryParameters
        call.respond(service.operations(call.bearer(), window, query["actorId"], query["action"]))
    }
    get("requests") {
        val window = call.window()
        val successful = call.request.queryParameters["successful"]?.let { it.toBooleanStrictOrNull() ?: invalid("successful 必须为布尔值") }
        call.respond(service.requests(call.bearer(), window, successful))
    }
}
