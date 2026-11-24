package dev.zerokotlin.ktor.system.excel

import dev.zerokotlin.ktor.bearer
import dev.zerokotlin.ktor.missing
import dev.zerokotlin.ktor.readWorkbook
import dev.zerokotlin.ktor.workbook
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** 用户、字典和字典项的 XLSX 入口。先授权，再解析 multipart，最后由 ExcelService 提交整批写入。 */
internal fun Route.excelController(service: ExcelService) {
    get("users/excel/template") {
        call.workbook("用户导入模板.xlsx", service.userTemplate(call.bearer()))
    }
    get("users/excel/export") {
        val token = call.bearer()
        val query = call.request.queryParameters
        val department = query["departmentId"]?.trim()?.ifEmpty { null }
        val role = query["roleId"]?.trim()?.ifEmpty { null }
        call.workbook("用户.xlsx", service.exportUsers(token, department, role))
    }
    post("users/excel/import") {
        val token = call.bearer()
        service.authorizeUserImport(token)
        val rows = call.readWorkbook(userColumns, 100)
        call.respond(HttpStatusCode.Created, service.importUsers(token, rows))
    }
    get("dictionaries/excel/template") {
        call.workbook("字典导入模板.xlsx", service.dictionaryTemplate(call.bearer()))
    }
    get("dictionaries/excel/export") {
        call.workbook("字典.xlsx", service.exportDictionaries(call.bearer()))
    }
    post("dictionaries/excel/import") {
        val token = call.bearer()
        service.authorizeDictionaryImport(token)
        val rows = call.readWorkbook(dictionaryColumns, 1000)
        call.respond(HttpStatusCode.Created, service.importDictionaries(token, rows))
    }
    get("dictionaries/{dictionaryId}/items/excel/template") {
        val token = call.bearer()
        val dictionary = call.parameters["dictionaryId"] ?: missing("字典")
        call.workbook("字典项导入模板.xlsx", service.itemTemplate(token, dictionary))
    }
    get("dictionaries/{dictionaryId}/items/excel/export") {
        val token = call.bearer()
        val dictionary = call.parameters["dictionaryId"] ?: missing("字典")
        call.workbook("字典项.xlsx", service.exportItems(token, dictionary))
    }
    post("dictionaries/{dictionaryId}/items/excel/import") {
        val token = call.bearer()
        val dictionary = call.parameters["dictionaryId"] ?: missing("字典")
        service.authorizeItemImport(token, dictionary)
        val rows = call.readWorkbook(itemColumns, 1000)
        call.respond(HttpStatusCode.Created, service.importItems(token, dictionary, rows))
    }
}
