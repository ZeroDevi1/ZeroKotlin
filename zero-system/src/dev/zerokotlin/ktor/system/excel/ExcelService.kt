package dev.zerokotlin.ktor.system.excel

import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.core.excel.XlsxColumn
import dev.zerokotlin.core.excel.XlsxRow
import dev.zerokotlin.core.excel.XlsxWorkbooks
import dev.zerokotlin.ktor.SystemStore
import dev.zerokotlin.ktor.Window
import dev.zerokotlin.ktor.invalid
import dev.zerokotlin.ktor.newId
import dev.zerokotlin.ktor.system.account.accountPassword
import dev.zerokotlin.ktor.system.account.insertUser
import dev.zerokotlin.ktor.system.account.requireAdministrator
import dev.zerokotlin.ktor.system.account.userManage
import dev.zerokotlin.ktor.system.account.userPage
import dev.zerokotlin.ktor.system.account.username
import dev.zerokotlin.ktor.system.dictionary.EditItem
import dev.zerokotlin.ktor.system.dictionary.dictionaryRead
import dev.zerokotlin.ktor.system.dictionary.dictionaryWrite
import dev.zerokotlin.ktor.system.dictionary.withDictionaries
import dev.zerokotlin.ktor.userRead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

private const val maxExportRows = 1000
internal val userColumns = listOf(
    XlsxColumn("用户名", 64), XlsxColumn("显示名称"), XlsxColumn("初始密码", 72),
    XlsxColumn("部门 ID", 36), XlsxColumn("角色 ID", 256 * 38),
)
internal val dictionaryColumns = listOf(XlsxColumn("字典编码", 120), XlsxColumn("字典名称"))
internal val itemColumns = listOf(
    XlsxColumn("值"), XlsxColumn("标签"), XlsxColumn("排序", textOnly = false), XlsxColumn("启用", textOnly = false),
)

@Serializable internal data class ExcelImport(val imported: Int)

private data class PreparedUser(
    val row: Int, val name: String, val displayName: String, val hash: String, val department: String?, val roles: Set<String>,
)

/** 导入复用账号和字典 Mapper，在同一事务保存批次与审计。 */
internal class ExcelService(private val store: SystemStore) {
    suspend fun userTemplate(token: String): ByteArray {
        store.write(token, userManage, administratorOnly = true) { _, _, audit -> audit("user.excel.template", "users") }
        return workbookTemplate(userColumns)
    }

    suspend fun exportUsers(token: String, department: String?, role: String?): ByteArray {
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
        return body
    }

    suspend fun authorizeUserImport(token: String) {
        store.read(token, userManage) { _, actor -> requireAdministrator(actor) }
    }

    suspend fun importUsers(token: String, input: List<XlsxRow>): ExcelImport {
        // BCrypt 在系统写锁之外完成，锁内复核调用者权限并原子保存整批用户。
        val rows = input.map { row ->
            val (rawName, rawDisplay, password, department, rawRoles) = row.cells
            atRow(row.number) {
                val name = username(rawName) ?: invalid("用户名格式无效")
                val display = store.plainName(rawDisplay)
                val roles = if (rawRoles.isBlank()) emptySet() else rawRoles.split(',').map(String::trim).toSet()
                if (roles.any { it.isEmpty() || it.length > 36 }) invalid("角色 ID 格式无效")
                PreparedUser(row.number, name, display, accountPassword(password), department.trim().ifEmpty { null }, roles)
            }
        }
        store.write(token, userManage, administratorOnly = true) { db, _, audit ->
            rows.forEach { row ->
                atRow(row.row) {
                    val id = newId()
                    store.insertUser(db, id, row.name, row.displayName, row.hash, row.department, row.roles)
                    audit("user.create", id)
                }
            }
        }
        return ExcelImport(rows.size)
    }

    suspend fun dictionaryTemplate(token: String): ByteArray {
        store.write(token, dictionaryWrite) { _, _, audit -> audit("dictionary.excel.template", "dictionaries") }
        return workbookTemplate(dictionaryColumns)
    }

    suspend fun exportDictionaries(token: String): ByteArray {
        val rows = store.read(token, dictionaryRead) { db, _ ->
            store.withDictionaries(db) {
                list(maxExportRows + 1).map { listOf(it.id, it.code, it.name, it.enabled) }
            }
        }
        if (rows.size > maxExportRows) invalid("导出最多包含 $maxExportRows 个字典")
        val workbook = workbookRows(listOf("字典 ID", "字典编码", "字典名称", "启用"), rows)
        store.write(token, dictionaryRead) { _, _, audit -> audit("dictionary.excel.export", "dictionaries") }
        return workbook
    }

    suspend fun authorizeDictionaryImport(token: String) {
        store.read(token, dictionaryWrite) { _, _ -> Unit }
    }

    suspend fun importDictionaries(token: String, rows: List<XlsxRow>): ExcelImport {
        store.write(token, dictionaryWrite) { db, _, audit ->
            store.withDictionaries(db) {
                rows.forEach { row ->
                    atRow(row.number) {
                        val id = newId()
                        insert(id, row.cells[0], row.cells[1])
                        audit("dictionary.create", id)
                    }
                }
            }
        }
        return ExcelImport(rows.size)
    }

    suspend fun itemTemplate(token: String, dictionary: String): ByteArray {
        store.write(token, dictionaryWrite) { db, _, audit ->
            store.withDictionaries(db) { requireDictionary(dictionary) }
            audit("dictionary.item.excel.template", dictionary)
        }
        return workbookTemplate(itemColumns)
    }

    suspend fun exportItems(token: String, dictionary: String): ByteArray {
        val rows = store.read(token, dictionaryRead) { db, _ ->
            store.withDictionaries(db) {
                requireDictionary(dictionary)
                items(dictionary, enabledOnly = false, limit = maxExportRows + 1).map {
                    listOf(it.id, it.value, it.label, it.sortOrder, it.enabled)
                }
            }
        }
        if (rows.size > maxExportRows) invalid("导出最多包含 $maxExportRows 个字典项")
        val workbook = workbookRows(listOf("字典项 ID") + itemColumns.map(XlsxColumn::heading), rows)
        store.write(token, dictionaryRead) { _, _, audit -> audit("dictionary.item.excel.export", dictionary) }
        return workbook
    }

    suspend fun authorizeItemImport(token: String, dictionary: String) {
        store.read(token, dictionaryWrite) { db, _ -> store.withDictionaries(db) { requireDictionary(dictionary) } }
    }

    suspend fun importItems(token: String, dictionary: String, rows: List<XlsxRow>): ExcelImport {
        store.write(token, dictionaryWrite) { db, _, audit ->
            store.withDictionaries(db) {
                requireDictionary(dictionary)
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
                        saveItem(dictionary, id, EditItem(value, label, order, active), create = true)
                        audit("dictionary.item.create", id)
                    }
                }
            }
        }
        return ExcelImport(rows.size)
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
