package dev.zerokotlin.ktor.system.inbox

import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.ktor.Window
import dev.zerokotlin.ktor.bearer
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** /api/inbox。只查询当前账号可见的已发布公告及其已读状态。 */
internal fun Route.inboxController(service: InboxService) = route("inbox") {
    get {
        val raw = call.bearer()
        val params = call.request.queryParameters
        val page = params["page"]?.let { it.toIntOrNull() ?: 0 } ?: 1
        val size = params["size"]?.let { it.toIntOrNull() ?: 0 } ?: 20
        if (page < 1 || size !in 1..100) throw SystemFailure(400, "page 必须大于等于 1，size 必须介于 1 和 100 之间")
        val unread = params["unreadOnly"]?.let {
            it.toBooleanStrictOrNull() ?: throw SystemFailure(400, "unreadOnly 必须为布尔值")
        } ?: false
        call.respond(service.page(raw, Window(page, size), unread))
    }
    put("read-all") {
        service.markRead(call.bearer(), null)
        call.respond(HttpStatusCode.NoContent)
    }
    put("{id}/read") {
        val raw = call.bearer()
        val id = call.parameters["id"] ?: throw SystemFailure(404, "公告不存在")
        service.markRead(raw, id)
        call.respond(HttpStatusCode.NoContent)
    }
}
