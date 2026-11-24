package dev.zerokotlin.ktor.system.permission.controller

import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.ktor.bearer
import dev.zerokotlin.ktor.system.permission.dto.CreatePermission
import dev.zerokotlin.ktor.system.permission.dto.UpdatePermission
import dev.zerokotlin.ktor.system.permission.service.PermissionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** /api/system/permissions。列表需要 read 权限，创建和更新还要求系统管理员。 */
internal fun Route.permissionController(service: PermissionService) = route("permissions") {
    get { call.respond(service.list(call.bearer())) }
    post {
        val token = call.bearer()
        call.respond(HttpStatusCode.Created, service.create(token, call.receive<CreatePermission>()))
    }
    put("{code}") {
        val token = call.bearer()
        val code = call.parameters["code"] ?: throw SystemFailure(400, "缺少权限编码")
        call.respond(service.update(token, code, call.receive<UpdatePermission>()))
    }
}
