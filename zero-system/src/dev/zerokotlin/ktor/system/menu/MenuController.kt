package dev.zerokotlin.ktor.system.menu

import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.bearer
import dev.zerokotlin.ktor.id
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** /api/system/menus。菜单树、创建和更新分别调用 MenuService。 */
internal fun Route.menuController(service: MenuService) = route("menus") {
    get { call.respond(service.tree(call.bearer())) }
    post {
        val token = call.bearer()
        call.respond(HttpStatusCode.Created, service.create(token, call.receive<MenuRequest>()))
    }
    put("{id}") {
        val token = call.bearer()
        service.update(token, call.id(), call.receive<MenuRequest>())
        call.respond(HttpStatusCode.NoContent)
    }
}
