package dev.zerokotlin.ktor.system.announcement

import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.bearer
import dev.zerokotlin.ktor.id
import dev.zerokotlin.ktor.window
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** /api/system/announcements。发布与撤回由 AnnouncementService 控制状态转换。 */
internal fun Route.announcementController(service: AnnouncementService) = route("announcements") {
    get { call.respond(service.list(call.bearer(), call.window())) }
    post {
        val token = call.bearer()
        call.respond(HttpStatusCode.Created, service.create(token, call.receive<EditAnnouncement>()))
    }
    put("{id}") {
        val token = call.bearer()
        service.update(token, call.id(), call.receive<EditAnnouncement>())
        call.respond(HttpStatusCode.NoContent)
    }
    post("{id}/publish") {
        service.publish(call.bearer(), call.id())
        call.respond(HttpStatusCode.NoContent)
    }
    post("{id}/revoke") {
        service.revoke(call.bearer(), call.id())
        call.respond(HttpStatusCode.NoContent)
    }
}
