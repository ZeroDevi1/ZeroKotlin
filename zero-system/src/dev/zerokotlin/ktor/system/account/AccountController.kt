package dev.zerokotlin.ktor.system.account

import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.DeleteBatch
import dev.zerokotlin.ktor.bearer
import dev.zerokotlin.ktor.id
import dev.zerokotlin.ktor.window
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** /api/system/users。请求绑定和响应由 Controller 处理，账号策略由 AccountService 执行。 */
internal fun Route.accountController(service: AccountService) = route("users") {
    get {
        val token = call.bearer()
        val window = call.window()
        val query = call.request.queryParameters
        val department = query["departmentId"]?.takeIf(String::isNotBlank)
        val role = query["roleId"]?.takeIf(String::isNotBlank)
        call.respond(service.list(token, window, department, role))
    }
    post {
        val token = call.bearer()
        call.respond(HttpStatusCode.Created, service.create(token, call.receive<CreateUser>()))
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
        patch {
            val token = call.bearer()
            val id = call.id()
            call.respond(service.editProfile(token, id, call.receive<EditUserProfile>()))
        }
        put("account") {
            val token = call.bearer()
            val id = call.id()
            service.editAccount(token, id, call.receive<EditUserAccount>())
            call.respond(HttpStatusCode.NoContent)
        }
        put("password") {
            val token = call.bearer()
            val id = call.id()
            service.resetPassword(token, id, call.receive<ResetUserPassword>().newPassword)
            call.respond(HttpStatusCode.NoContent)
        }
        delete {
            service.delete(call.bearer(), listOf(call.id()))
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
