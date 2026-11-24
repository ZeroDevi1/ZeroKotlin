package dev.zerokotlin.ktor.system.department

import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.bearer
import dev.zerokotlin.ktor.id
import dev.zerokotlin.ktor.system.account.AccountService
import dev.zerokotlin.ktor.window
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** /api/system/departments。成员查询复用 AccountService 的行级查询。 */
internal fun Route.departmentController(service: DepartmentService, accounts: AccountService) = route("departments") {
    get("tree") { call.respond(service.tree(call.bearer())) }
    get("{id}/members") {
        val window = call.window()
        val department = call.id()
        val roleId = call.request.queryParameters["roleId"]?.takeIf(String::isNotBlank)
        call.respond(accounts.list(call.bearer(), window, department, roleId))
    }
    post {
        val token = call.bearer()
        call.respond(HttpStatusCode.Created, service.create(token, call.receive<DepartmentRequest>()))
    }
    put("{id}") {
        val token = call.bearer()
        service.update(token, call.id(), call.receive<DepartmentRequest>())
        call.respond(HttpStatusCode.NoContent)
    }
}
