package dev.zerokotlin.core.excel

import dev.zerokotlin.core.SystemFailure
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.util.NumberToTextConverter
import org.apache.poi.xssf.usermodel.XSSFWorkbook

/** Excel 工作簿的媒体类型。 */
const val XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/**
 * 导入模板的列定义。
 *
 * @property heading 第一行使用的列名。
 * @property maxLength 读取单元格时允许的最大文本长度，以 UTF-16 代码单元计。
 * @property textOnly 是否要求以文本保存，以保留前导零和原始字符。
 */
data class XlsxColumn(val heading: String, val maxLength: Int = 256, val textOnly: Boolean = true)

/**
 * 从工作簿读取的一行导入数据。
 *
 * @property number Excel 界面显示的行号，从 1 开始。
 * @property cells 按模板顺序排列的单元格文本，空单元格用空字符串表示。
 */
class XlsxRow(val number: Int, val cells: List<String>)

/** 读写受限的单表 XLSX 工作簿。错误消息仅包含列名或行号等结构信息。 */
object XlsxWorkbooks {
    private const val MAX_FILE_BYTES = 1024 * 1024
    private const val MAX_UNCOMPRESSED_BYTES = 16 * 1024 * 1024
    private const val MAX_ZIP_ENTRIES = 64

    /**
     * 按模板读取上传文件，跳过空白行，拒绝公式、额外列、宏以及超过限制的工作簿。
     *
     * @param name 名称以 .xlsx 结尾的上传文件名。
     * @param bytes 上传内容，最多 1 MiB。
     * @param columns 依次排列的表头及单元格类型限制。
     * @param maxRows 表头下可填写的最大数据行数。
     * @return 含 Excel 行号的数据行，按表内顺序排列。
     */
    fun read(name: String?, bytes: ByteArray, columns: List<XlsxColumn>, maxRows: Int): List<XlsxRow> {
        if (bytes.isEmpty() || name?.endsWith(".xlsx", ignoreCase = true) != true) invalid("请上传非空的 .xlsx 文件")
        if (bytes.size > MAX_FILE_BYTES) tooLarge()
        return try {
            checkArchive(bytes)
            XSSFWorkbook(bytes.inputStream()).use { workbook ->
                if (workbook.isMacroEnabled || workbook.numberOfSheets != 1 || workbook.isSheetHidden(0) ||
                    workbook.isSheetVeryHidden(0)
                ) invalid("请使用只含一张可见工作表且不含宏的 .xlsx 文件")
                val sheet = workbook.getSheetAt(0)
                val header = sheet.getRow(0)?.readCells(columns)
                if (header != columns.map(XlsxColumn::heading)) invalid("第 1 行表头与下载模板不一致")
                buildList {
                    for (row in sheet) {
                        if (row.rowNum == 0) continue
                        val cells = row.readCells(columns)
                        if (cells.all(String::isEmpty)) continue
                        if (row.rowNum > maxRows) invalid("第 ${row.rowNum + 1} 行超出模板的 $maxRows 条数据行上限")
                        add(XlsxRow(row.rowNum + 1, cells))
                    }
                    if (isEmpty()) invalid("工作表没有可导入的数据行")
                }
            }
        } catch (error: SystemFailure) {
            throw error
        } catch (_: Exception) {
            invalid("无法读取 .xlsx 文件，请使用下载的模板")
        }
    }

    /** 按列生成空白模板，并为需要保留原文的列设置文本格式。 */
    fun template(columns: List<XlsxColumn>): ByteArray =
        write(columns.map(XlsxColumn::heading), emptyList(), columns.indices.filter { columns[it].textOnly })

    /**
     * 输出由调用方白名单指定的列及行。字符串始终作为文本写入。
     *
     * @param headings 工作表第一行的列名。
     * @param rows 列值依次为字符串、布尔值、整数或空值的数据行。
     * @return 可直接下载的 XLSX 文件。
     */
    fun write(headings: List<String>, rows: List<List<Any?>>): ByteArray = write(headings, rows, emptyList())

    private fun write(headings: List<String>, rows: List<List<Any?>>, textColumns: List<Int>): ByteArray =
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("数据")
            sheet.createFreezePane(0, 1)
            val textStyle = workbook.createCellStyle().apply { dataFormat = workbook.createDataFormat().getFormat("@") }
            val headerStyle = workbook.createCellStyle().apply {
                setFont(workbook.createFont().apply { bold = true })
            }
            val header = sheet.createRow(0)
            headings.forEachIndexed { index, label ->
                sheet.setColumnWidth(index, 24 * 256)
                if (index in textColumns) sheet.setDefaultColumnStyle(index, textStyle)
                header.createCell(index).apply {
                    setCellValue(label)
                    setCellStyle(headerStyle)
                }
            }
            rows.forEachIndexed { index, values ->
                check(values.size == headings.size)
                val row = sheet.createRow(index + 1)
                values.forEachIndexed { column, value ->
                    when (value) {
                        null -> Unit
                        is String -> row.createCell(column).setCellValue(value)
                        is Int -> row.createCell(column).setCellValue(value.toDouble())
                        is Boolean -> row.createCell(column).setCellValue(value)
                        else -> error("Excel 列值类型无效")
                    }
                }
            }
            ByteArrayOutputStream().also(workbook::write).toByteArray()
        }

    /** 在 POI 加载工作簿之前检查 ZIP 条目数量及实际解压体积。 */
    private fun checkArchive(bytes: ByteArray) {
        ZipInputStream(bytes.inputStream()).use { archive ->
            val names = mutableSetOf<String>()
            val buffer = ByteArray(8192)
            var expanded = 0
            while (true) {
                val entry = archive.nextEntry ?: break
                if (!names.add(entry.name)) invalid("Excel 压缩包内存在重复条目")
                if (names.size > MAX_ZIP_ENTRIES) tooLarge()
                while (true) {
                    val read = archive.read(buffer)
                    if (read == -1) break
                    expanded += read
                    if (expanded > MAX_UNCOMPRESSED_BYTES) tooLarge()
                }
            }
            if ("[Content_Types].xml" !in names) invalid("文件不是有效的 .xlsx 工作簿")
        }
    }

    private fun Row.readCells(columns: List<XlsxColumn>): List<String> {
        val line = rowNum + 1
        if (any { it.columnIndex >= columns.size && it.cellType != CellType.BLANK }) {
            invalid("第 $line 行包含模板以外的列")
        }
        return columns.mapIndexed { index, column ->
            val cell = getCell(index) ?: return@mapIndexed ""
            val error = "第 $line 行「${column.heading}」"
            val value = when (cell.cellType) {
                CellType.BLANK -> ""
                CellType.STRING -> cell.stringCellValue
                CellType.NUMERIC -> {
                    if (column.textOnly || rowNum == 0) invalid("$error 必须为文本")
                    NumberToTextConverter.toText(cell.numericCellValue)
                }
                CellType.BOOLEAN -> {
                    if (column.textOnly || rowNum == 0) invalid("$error 必须为文本")
                    cell.booleanCellValue.toString()
                }
                else -> invalid("$error 只接受普通值，不能使用公式或错误单元格")
            }
            if (value.length > column.maxLength) invalid("$error 最多允许 ${column.maxLength} 字符")
            value
        }
    }

    private fun invalid(message: String): Nothing = throw SystemFailure(400, message)

    private fun tooLarge(): Nothing =
        throw SystemFailure(413, "Excel 文件超过上传或解压大小限制")
}
