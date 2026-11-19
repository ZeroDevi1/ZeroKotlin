package dev.zerokotlin.system.support

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/** Excel 下载的最大数据行数，查询多取一行用于判断是否超限。 */
internal const val EXCEL_EXPORT_ROWS = 1000

/**
 * 由模板解析出的业务请求及其 Excel 行号。
 *
 * @property number 原始行号，从 1 开始。
 * @property request 已读取的请求字段。
 */
class ExcelImportRow<T>(val number: Int, val request: T)

/** 给导入校验与唯一性冲突加上行号，同时保留既有 HTTP 状态码。 */
internal fun <T> atExcelRow(number: Int, action: () -> T): T = try {
    action()
} catch (error: ResponseStatusException) {
    if (error.statusCode != HttpStatus.BAD_REQUEST && error.statusCode != HttpStatus.CONFLICT) throw error
    throw ResponseStatusException(error.statusCode, "第 $number 行：${error.reason ?: "数据无效"}")
}
