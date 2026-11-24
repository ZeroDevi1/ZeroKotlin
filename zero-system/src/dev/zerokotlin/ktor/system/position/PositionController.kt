package dev.zerokotlin.ktor.system.position

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

/** /api/system/positions。岗位和成员接口进入同一个 PositionService。 */
internal fun Route.positionController(service: PositionService) = route("positions") {
    get { call.respond(service.list(call.bearer(), call.window())) }
    post {
        val token = call.bearer()
        call.respond(HttpStatusCode.Created, service.create(token, call.receive<NewPosition>()))
    }
    route("{id}") {
        put {
            val token = call.bearer()
            service.update(token, call.id(), call.receive<EditPosition>())
            call.respond(HttpStatusCode.NoContent)
        }
        get("members") {
            val id = call.id()
            call.respond(service.members(call.bearer(), id))
        }
        put("members") {
            val token = call.bearer()
            service.replaceMembers(token, call.id(), call.receive<PositionMembers>().userIds)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
