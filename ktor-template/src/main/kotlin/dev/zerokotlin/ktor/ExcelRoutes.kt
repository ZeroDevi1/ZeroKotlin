package dev.zerokotlin.ktor

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.HexFormat
import kotlin.coroutines.coroutineContext

private const val maxWorkbook = 1024 * 1024
private const val maxExportRows = 1000
private val userColumns = listOf(
    XlsxColumn("用户名", 64), XlsxColumn("显示名称"), XlsxColumn("初始密码", 72),
    XlsxColumn("部门 ID", 36), XlsxColumn("角色 ID", 256 * 38),
)
private val dictionaryColumns = listOf(XlsxColumn("字典编码", 120), XlsxColumn("字典名称"))
private val itemColumns = listOf(
    XlsxColumn("值"), XlsxColumn("标签"), XlsxColumn("排序", textOnly = false), XlsxColumn("启用", textOnly = false),
)
@Serializable private data class ExcelImport(val imported: Int)
private data class PreparedUser(
    val row: Int, val name: String, val displayName: String, val hash: String, val department: String?, val roles: Set<String>,
)

internal fun Route.excelRoutes(store: SystemStore) {
    get("users/excel/template") {
        val token = call.bearer()
        store.write(token, userManage, administratorOnly = true) { _, _, audit -> audit("user.excel.template", "users") }
        call.workbook("用户导入模板.xlsx", workbookTemplate(userColumns))
    }
    get("users/excel/export") {
        val token = call.bearer()
        val query = call.request.queryParameters
        val department = query["departmentId"]?.trim()?.ifEmpty { null }
        val role = query["roleId"]?.trim()?.ifEmpty { null }
        val rows = store.read(token, userRead) { db, actor ->
            val first = db.userPage(department, role, Window(1, 100), actor)
            if (first.total > maxExportRows) invalid("导出最多包含 $maxExportRows 个用户，请缩小筛选范围")
            (first.items + (2..((first.total + 99) / 100).toInt()).flatMap {
                db.userPage(department, role, Window(it, 100), actor).items
            }).map { (user, roles) ->
                listOf(user.id, user.username, user.displayName, user.departmentId, user.enabled, roles.joinToString(",") { it.id })
            }
        }
        val body = workbookRows(listOf("用户 ID", "用户名", "显示名称", "部门 ID", "启用", "角色 ID"), rows)
        store.write(token, userRead) { _, _, audit -> audit("user.excel.export", "users") }
        call.workbook("用户.xlsx", body)
    }
    post("users/excel/import") {
        val token = call.bearer()
        store.read(token, userManage) { _, actor -> requireAdministrator(actor) }
        val rows = call.readWorkbook(userColumns, 100).map { row ->
            val (rawName, rawDisplay, password, department, rawRoles) = row.cells
            atRow(row.number) {
                val name = username(rawName) ?: invalid("用户名格式无效")
                val display = store.plainName(rawDisplay)
                val roles = if (rawRoles.isBlank()) emptySet() else rawRoles.split(',').map(String::trim).toSet()
                if (roles.any { it.isEmpty() || it.length > 36 }) invalid("角色 ID 格式无效")
                PreparedUser(row.number, name, display, store.accountPassword(password), department.trim().ifEmpty { null }, roles)
            }
        }
        store.write(token, userManage, administratorOnly = true) { db, _, audit ->
            rows.forEach { row ->
                atRow(row.row) {
                    val id = newId()
                    db.insertUser(id, row.name, row.displayName, row.hash, row.department, row.roles)
                    audit("user.create", id)
                }
            }
        }
        call.respond(HttpStatusCode.Created, ExcelImport(rows.size))
    }
    get("dictionaries/excel/template") {
        val token = call.bearer()
        store.write(token, dictionaryWrite) { _, _, audit -> audit("dictionary.excel.template", "dictionaries") }
        call.workbook("字典导入模板.xlsx", workbookTemplate(dictionaryColumns))
    }
    get("dictionaries/excel/export") {
        val token = call.bearer()
        val rows = store.read(token, dictionaryRead) { db, _ ->
            db.rows("SELECT id, code, name, enabled FROM sys_dictionary ORDER BY code LIMIT ?", maxExportRows + 1) {
                listOf(it.getString(1), it.getString(2), it.getString(3), it.getInt(4) == 1)
            }
        }
        if (rows.size > maxExportRows) invalid("导出最多包含 $maxExportRows 个字典")
        val workbook = workbookRows(listOf("字典 ID", "字典编码", "字典名称", "启用"), rows)
        store.write(token, dictionaryRead) { _, _, audit -> audit("dictionary.excel.export", "dictionaries") }
        call.workbook("字典.xlsx", workbook)
    }
    post("dictionaries/excel/import") {
        val token = call.bearer()
        store.read(token, dictionaryWrite) { _, _ -> Unit }
        val rows = call.readWorkbook(dictionaryColumns, 1000)
        store.write(token, dictionaryWrite) { db, _, audit ->
            rows.forEach { row ->
                atRow(row.number) {
                    val id = newId()
                    db.insertDictionary(id, row.cells[0], row.cells[1])
                    audit("dictionary.create", id)
                }
            }
        }
        call.respond(HttpStatusCode.Created, ExcelImport(rows.size))
    }
    get("dictionaries/{dictionaryId}/items/excel/template") {
        val token = call.bearer()
        val dictionary = call.parameters["dictionaryId"] ?: missing("字典")
        store.write(token, dictionaryWrite) { db, _, audit ->
            db.requireDictionary(dictionary)
            audit("dictionary.item.excel.template", dictionary)
        }
        call.workbook("字典项导入模板.xlsx", workbookTemplate(itemColumns))
    }
    get("dictionaries/{dictionaryId}/items/excel/export") {
        val token = call.bearer()
        val dictionary = call.parameters["dictionaryId"] ?: missing("字典")
        val rows = store.read(token, dictionaryRead) { db, _ ->
            db.requireDictionary(dictionary)
            db.rows(
                """SELECT id, item_value, label, sort_order, enabled FROM sys_dictionary_item
                   WHERE dictionary_id = ? ORDER BY sort_order, id LIMIT ?""", dictionary, maxExportRows + 1,
            ) { listOf(it.getString(1), it.getString(2), it.getString(3), it.getInt(4), it.getInt(5) == 1) }
        }
        if (rows.size > maxExportRows) invalid("导出最多包含 $maxExportRows 个字典项")
        val workbook = workbookRows(listOf("字典项 ID") + itemColumns.map(XlsxColumn::heading), rows)
        store.write(token, dictionaryRead) { _, _, audit -> audit("dictionary.item.excel.export", dictionary) }
        call.workbook("字典项.xlsx", workbook)
    }
    post("dictionaries/{dictionaryId}/items/excel/import") {
        val token = call.bearer()
        val dictionary = call.parameters["dictionaryId"] ?: missing("字典")
        store.read(token, dictionaryWrite) { db, _ -> db.requireDictionary(dictionary) }
        val rows = call.readWorkbook(itemColumns, 1000)
        store.write(token, dictionaryWrite) { db, _, audit ->
            db.requireDictionary(dictionary)
            rows.forEach { row ->
                atRow(row.number) {
                    val (value, label, sort, enabled) = row.cells
                    val order = sort.trim().ifEmpty { "0" }.toIntOrNull() ?: invalid("排序必须为 32 位整数")
                    val active = when (enabled.trim().lowercase()) {
                        "", "1", "true" -> true
                        "0", "false" -> false
                        else -> invalid("启用须为 TRUE、FALSE、1 或 0")
                    }
                    val id = newId()
                    db.saveItem(dictionary, id, EditItem(value, label, order, active), create = true)
                    audit("dictionary.item.create", id)
                }
            }
        }
        call.respond(HttpStatusCode.Created, ExcelImport(rows.size))
    }
}

private inline fun <T> atRow(number: Int, action: () -> T): T = try {
    action()
} catch (failure: SystemFailure) {
    throw SystemFailure(failure.status, "第 $number 行：${failure.message}")
}

private suspend fun workbookTemplate(columns: List<XlsxColumn>) = withContext(Dispatchers.IO) { XlsxWorkbooks.template(columns) }

private suspend fun workbookRows(headings: List<String>, rows: List<List<Any?>>) =
    withContext(Dispatchers.IO) { XlsxWorkbooks.write(headings, rows) }

internal suspend fun ApplicationCall.readWorkbook(columns: List<XlsxColumn>, rows: Int): List<XlsxRow> {
    var bytes: ByteArray? = null
    var filename: String? = null
    receiveMultipart(formFieldLimit = multipartLimit).forEachPart { part ->
        try {
            if (part is PartData.FileItem && part.name == "file") {
                if (bytes != null) invalid("每次只能上传一个工作簿")
                filename = part.originalFileName
                bytes = withContext(Dispatchers.IO) {
                    part.provider().toInputStream(coroutineContext[Job]).use { it.readNBytes(maxWorkbook + 1) }
                }
                if (checkNotNull(bytes).size > maxWorkbook) throw SystemFailure(413, "Excel 文件超过 1 MiB")
            }
        } finally {
            part.release()
        }
    }
    val input = bytes ?: invalid("缺少 .xlsx 文件")
    return withContext(Dispatchers.IO) { XlsxWorkbooks.read(filename, input, columns, rows) }
}

internal suspend fun ApplicationCall.workbook(name: String, bytes: ByteArray) {
    val encoded = HexFormat.of().withPrefix("%").withUpperCase().formatHex(name.toByteArray())
    response.header(HttpHeaders.ContentDisposition, "attachment; filename*=UTF-8''$encoded")
    response.header("X-Content-Type-Options", "nosniff")
    respondBytes(bytes, ContentType.parse(XLSX_MEDIA_TYPE))
}
