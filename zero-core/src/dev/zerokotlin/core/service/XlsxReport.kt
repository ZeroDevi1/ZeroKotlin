package dev.zerokotlin.core.service

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import org.springframework.http.HttpStatus
import org.springframework.util.ReflectionUtils
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

/** 显式选入 XLSX 的字段。列名和顺序在同一个报表模型中须唯一。 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ReportColumn(val name: String, val order: Int)

/** 保留上传文件中从 1 开始的行号及解析出的业务对象。 */
data class ReportRow<T>(val number: Int, val value: T)

/** 使用字段注解生成 XLSX，模板和导入共用同一份列定义。 */
class XlsxReport<T : Any>(type: Class<T>) {
    private val fields = buildList {
        ReflectionUtils.doWithFields(type) { field ->
            val column = field.getAnnotation(ReportColumn::class.java) ?: return@doWithFields
            require(!Modifier.isStatic(field.modifiers) && CrudValueConversion.supports(field.type)) {
                "${type.name}.${field.name} 不是可读写的报表字段类型"
            }
            require(column.name.isNotBlank() && column.name.length <= MAX_CELL_LENGTH)
            ReflectionUtils.makeAccessible(field)
            add(field to column)
        }
    }.sortedBy { it.second.order }.map { (field, column) -> Column(field, column) }.also { columns ->
        require(columns.isNotEmpty()) { "${type.name} 须声明至少一列 @ReportColumn" }
        require(columns.distinctBy { it.order }.size == columns.size && columns.distinctBy { it.heading }.size == columns.size) {
            "${type.name} 的报表列名和顺序须唯一"
        }
    }
    private val columns = fields.map { it.xlsx }

    /** 生成仅含表头的导入模板。 */
    fun template(): ByteArray = XlsxWorkbooks.template(columns)

    /** 从上传文件读取报表字段。空单元格保留对象的初始值。 */
    fun read(file: MultipartFile, factory: () -> T): List<ReportRow<T>> {
        require(fields.none { Modifier.isFinal(it.field.modifiers) }) { "导入字段须为可写属性" }
        return XlsxWorkbooks.read(file, columns, REPORT_ROWS).map { row ->
            val value = factory()
            for ((index, cell) in row.cells.withIndex()) {
                if (cell.isEmpty()) continue
                val column = fields[index]
                val parsed = try {
                    CrudValueConversion.parse(cell, column.field.type)
                } catch (_: IllegalArgumentException) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "第 ${row.number} 行「${column.heading}」格式无效")
                }
                column.field.set(value, parsed)
            }
            ReportRow(row.number, value)
        }
    }

    /** 导出标注字段。大整数、小数和日期使用文本保留原始精度及表示。 */
    fun write(rows: List<T>): ByteArray = XlsxWorkbooks.write(columns.map(XlsxColumn::heading), rows.map { row ->
        fields.map { column ->
            when (val value = column.field.get(row)) {
                null, is Int, is Boolean -> value
                else -> {
                    val text = if (value is Enum<*>) value.name else value.toString()
                    if (text.length > MAX_CELL_LENGTH) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "「${column.heading}」超出 Excel 单元格长度")
                    }
                    text
                }
            }
        }
    })

    private class Column(val field: Field, annotation: ReportColumn) {
        val heading = annotation.name
        val order = annotation.order
        val xlsx = XlsxColumn(
            heading,
            maxLength = MAX_CELL_LENGTH,
            textOnly = field.type !in setOf(Int::class.java, Int::class.javaObjectType, Boolean::class.java, Boolean::class.javaObjectType),
        )
    }
}

internal const val REPORT_ROWS = 1000
private const val MAX_CELL_LENGTH = 32_767
