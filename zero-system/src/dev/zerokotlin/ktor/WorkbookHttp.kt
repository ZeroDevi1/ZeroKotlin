package dev.zerokotlin.ktor

import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.core.excel.XLSX_MEDIA_TYPE
import dev.zerokotlin.core.excel.XlsxColumn
import dev.zerokotlin.core.excel.XlsxRow
import dev.zerokotlin.core.excel.XlsxWorkbooks
import dev.zerokotlin.ktor.system.file.multipartLimit
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.util.HexFormat
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

private const val maxWorkbook = 1024 * 1024

/** 读取受限的 multipart XLSX，并释放每个 part；调用前由业务路由完成权限校验。 */
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
