package dev.zerokotlin.system.service

import dev.zerokotlin.core.excel.XlsxColumn
import dev.zerokotlin.core.excel.XlsxWorkbooks
import dev.zerokotlin.system.dto.CreateDictionaryRequest
import dev.zerokotlin.system.dto.CreateUserRequest
import dev.zerokotlin.system.dto.DictionaryItemRequest
import dev.zerokotlin.system.dto.ExcelImportResult
import dev.zerokotlin.system.security.DICTIONARY_READ
import dev.zerokotlin.system.security.DICTIONARY_WRITE
import dev.zerokotlin.system.security.USER_MANAGE
import dev.zerokotlin.system.security.USER_READ
import dev.zerokotlin.system.security.currentSystemActor
import dev.zerokotlin.system.support.ExcelImportRow
import dev.zerokotlin.system.support.atExcelRow
import dev.zerokotlin.system.support.invalid
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile

/** 定义系统资源的 Excel 列及转换规则，并将导入交给现有业务写入事务。 */
@Service
class SystemExcel(
    private val users: SystemUsers,
    private val dictionaries: SystemDictionaries,
    private val writes: SystemWrites,
    private val transactions: TransactionTemplate,
) {
    private val userColumns = listOf(
        XlsxColumn("用户名", 64), XlsxColumn("显示名称"), XlsxColumn("初始密码", 72),
        XlsxColumn("部门 ID", 36), XlsxColumn("角色 ID", 256 * 38),
    )
    private val dictionaryColumns = listOf(XlsxColumn("字典编码", 120), XlsxColumn("字典名称"))
    private val itemColumns = listOf(
        XlsxColumn("值"), XlsxColumn("标签"),
        XlsxColumn("排序", textOnly = false), XlsxColumn("启用", textOnly = false),
    )

    /** 下载新增账号模板，要求内置管理员身份。 */
    fun userTemplate(): ByteArray {
        currentSystemActor().requireAdministrator()
        return auditedDownload(
            XlsxWorkbooks.template(userColumns), USER_MANAGE, "user.excel.template", "users", administratorOnly = true,
        )
    }

    /** 下载新增字典模板。 */
    fun dictionaryTemplate(): ByteArray = auditedDownload(
        XlsxWorkbooks.template(dictionaryColumns), DICTIONARY_WRITE, "dictionary.excel.template", "dictionaries",
    )

    /** 下载新增字典项模板，目标字典必须存在。 */
    fun itemTemplate(dictionaryId: String): ByteArray {
        dictionaries.requireDictionary(dictionaryId)
        return auditedDownload(
            XlsxWorkbooks.template(itemColumns), DICTIONARY_WRITE, "dictionary.item.excel.template", dictionaryId,
        )
    }

    /** 导出部门和角色筛选条件内的可见账号及角色关系。 */
    fun exportUsers(departmentId: String?, roleId: String?): ByteArray {
        val rows = users.forExcel(departmentId, roleId).map { (user, roles) ->
            listOf(user.id, user.username, user.displayName, user.departmentId, user.enabled, roles.joinToString(",") { it.id })
        }
        val headings = listOf("用户 ID", "用户名", "显示名称", "部门 ID", "启用", "角色 ID")
        return auditedDownload(XlsxWorkbooks.write(headings, rows), USER_READ, "user.excel.export", "users")
    }

    /** 导出字典管理目录，包含停用字典。 */
    fun exportDictionaries(): ByteArray {
        val rows = dictionaries.dictionariesForExcel().map { listOf(it.id, it.code, it.name, it.enabled) }
        val workbook = XlsxWorkbooks.write(listOf("字典 ID", "字典编码", "字典名称", "启用"), rows)
        return auditedDownload(workbook, DICTIONARY_READ, "dictionary.excel.export", "dictionaries")
    }

    /** 导出指定字典的管理选项，包含停用项。 */
    fun exportItems(dictionaryId: String): ByteArray {
        val rows = dictionaries.itemsForExcel(dictionaryId).map { listOf(it.id, it.value, it.label, it.sortOrder, it.enabled) }
        val workbook = XlsxWorkbooks.write(listOf("字典项 ID") + itemColumns.map(XlsxColumn::heading), rows)
        return auditedDownload(workbook, DICTIONARY_READ, "dictionary.item.excel.export", dictionaryId)
    }

    /** 从用户模板读取逐行提供的初始密码及业务角色，新增账号。 */
    fun importUsers(file: MultipartFile): ExcelImportResult {
        currentSystemActor().requireAdministrator()
        val rows = XlsxWorkbooks.read(file, userColumns, maxRows = 100).map { row ->
            val (username, displayName, password, department, roles) = row.cells
            val roleIds = atExcelRow(row.number) {
                if (roles.isBlank()) emptySet() else roles.split(',').map { it.trim() }.toSet().also { ids ->
                    if (ids.any { it.isEmpty() || it.length > 36 }) invalid("角色 ID 列格式无效，请使用半角逗号分隔")
                }
            }
            val request = CreateUserRequest(username, displayName, password, department.trim().ifEmpty { null }, roleIds)
            ExcelImportRow(row.number, request)
        }
        users.importExcel(rows)
        return ExcelImportResult(rows.size)
    }

    /** 从字典模板读取编码、名称，并批量新增启用字典。 */
    fun importDictionaries(file: MultipartFile): ExcelImportResult {
        val rows = XlsxWorkbooks.read(file, dictionaryColumns, maxRows = 1000).map { row ->
            val (code, name) = row.cells
            ExcelImportRow(row.number, CreateDictionaryRequest(code, name))
        }
        dictionaries.importExcel(rows)
        return ExcelImportResult(rows.size)
    }

    /** 从字典项模板向指定字典新增选项，省略排序和状态时沿用单条创建的默认值。 */
    fun importItems(dictionaryId: String, file: MultipartFile): ExcelImportResult {
        val rows = XlsxWorkbooks.read(file, itemColumns, maxRows = 1000).map { row ->
            val (value, label, sort, enabled) = row.cells
            val request = atExcelRow(row.number) {
                val order = sort.trim().ifEmpty { "0" }.toIntOrNull() ?: invalid("排序必须为 32 位整数")
                val active = when (enabled.trim().lowercase()) {
                    "", "1", "true" -> true
                    "0", "false" -> false
                    else -> invalid("启用须为 TRUE、FALSE、1 或 0")
                }
                DictionaryItemRequest(value, label, order, active)
            }
            ExcelImportRow(row.number, request)
        }
        dictionaries.importItemsExcel(dictionaryId, rows)
        return ExcelImportResult(rows.size)
    }

    /** 工作簿生成后重新校验会话并记录下载事件，审计成功后才能返回文件。 */
    private fun auditedDownload(
        workbook: ByteArray,
        permission: String,
        action: String,
        targetId: String,
        administratorOnly: Boolean = false,
    ): ByteArray {
        transactions.executeWithoutResult {
            writes.record(writes.authorize(permission, administratorOnly), action, targetId)
        }
        return workbook
    }
}
