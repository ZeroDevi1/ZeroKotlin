package dev.zerokotlin.ktor.system.role

import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.DeleteBatch
import dev.zerokotlin.ktor.bearer
import dev.zerokotlin.ktor.id
import dev.zerokotlin.ktor.invalid
import dev.zerokotlin.ktor.window
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** /api/system/roles。路由统一进入 RoleService，写操作需要系统管理员。 */
internal fun Route.roleController(service: RoleService) = route("roles") {
    get {
        val window = call.window()
        val enabled = call.request.queryParameters["enabled"]?.let { it.toBooleanStrictOrNull() ?: invalid("enabled 必须为布尔值") }
        call.respond(service.list(call.bearer(), window, enabled))
    }
    post {
        val token = call.bearer()
        call.respond(HttpStatusCode.Created, service.create(token, call.receive<CreateRole>()))
    }
    post("batch-delete") {
        val token = call.bearer()
        service.delete(token, call.receive<DeleteBatch>().ids)
        call.respond(HttpStatusCode.NoContent)
    }
    route("{id}") {
        get {
            val id = call.id()
            call.respond(service.detail(call.bearer(), id))
        }
        put {
            val token = call.bearer()
            service.update(token, call.id(), call.receive<UpdateRole>())
            call.respond(HttpStatusCode.NoContent)
        }
        delete {
            service.delete(call.bearer(), listOf(call.id()))
            call.respond(HttpStatusCode.NoContent)
        }
        get("menus") {
            val id = call.id()
            call.respond(service.menus(call.bearer(), id))
        }
        put("menus") {
            val token = call.bearer()
            service.updateMenus(token, call.id(), call.receive<RoleMenus>())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
