package dev.zerokotlin.ktor

import dev.zerokotlin.core.excel.XlsxWorkbooks
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** 通用 HTTP 请求与 XLSX 响应适配。权限与事务由传入的 Service 处理。 */
internal class ResourceHttp(private val service: ResourceService) {
    suspend fun list(call: ApplicationCall) {
        call.respond(service.list(call.bearer(), call.window(), call.request.queryParameters.entries().associate { it.key to it.value }))
    }

    suspend fun find(call: ApplicationCall) {
        call.respond(service.find(call.bearer(), call.id()))
    }

    suspend fun create(call: ApplicationCall) {
        val token = call.bearer()
        val body = if (service.acceptsCreateBody) call.receive<JsonObject>() else buildJsonObject {}
        call.respond(HttpStatusCode.Created, service.create(token, body))
    }

    suspend fun update(call: ApplicationCall) {
        service.update(call.bearer(), call.id(), call.receive<JsonObject>())
        call.respond(HttpStatusCode.NoContent)
    }

    suspend fun delete(call: ApplicationCall) {
        service.delete(call.bearer(), call.id())
        call.respond(HttpStatusCode.NoContent)
    }

    suspend fun export(call: ApplicationCall) {
        val rows = service.export(call.bearer(), call.request.queryParameters.entries().associate { it.key to it.value })
        call.workbook("记录.xlsx", rows)
    }

    suspend fun template(call: ApplicationCall) {
        service.authorizeImport(call.bearer())
        val bytes = withContext(Dispatchers.IO) { XlsxWorkbooks.template(service.importColumns) }
        call.workbook("导入模板.xlsx", bytes)
    }

    suspend fun importRows(call: ApplicationCall) {
        val token = call.bearer()
        service.authorizeImport(token)
        val rows = call.readWorkbook(service.importColumns, 1000)
        service.importRows(token, rows)
        call.respond(HttpStatusCode.Created, buildJsonObject { put("imported", JsonPrimitive(rows.size)) })
    }
}

/** 路由兼容旧版单文件脚手架。新版 Controller 显式生成同一套路由声明。 */
internal fun Route.registerResourceRoutes(path: String, http: ResourceHttp, service: ResourceService) {
    route(path) {
        get { http.list(call) }
        get("{id}") { http.find(call) }
        post { http.create(call) }
        if (service.supportsUpdate) put("{id}") { http.update(call) }
        delete("{id}") { http.delete(call) }
        get("excel/export") { http.export(call) }
        if (service.importColumns.isNotEmpty()) {
            get("excel/template") { http.template(call) }
            post("excel/import") { http.importRows(call) }
        }
    }
}
