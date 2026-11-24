package dev.zerokotlin.ktor.system.file

import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.bearer
import dev.zerokotlin.ktor.id
import dev.zerokotlin.ktor.invalid
import dev.zerokotlin.ktor.window
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import java.nio.file.Files
import java.util.HexFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

private const val fileLimit = 50L * 1024 * 1024
// 总请求上限额外留出 multipart 边界和表单字段的空间。
internal const val multipartLimit = 51L * 1024 * 1024

/** /api/system/files。只处理参数、multipart 临时文件和下载响应。 */
internal fun Route.fileController(service: FileService) = route("files") {
    get {
        val token = call.bearer()
        val window = call.window()
        val query = call.request.queryParameters
        val name = query["name"]?.trim()?.ifEmpty { null }
        val category = query["bizType"]?.trim()?.ifEmpty { null }?.let(::fileBusinessCode)
        call.respond(service.list(token, window, name, category))
    }
    post {
        val token = call.bearer()
        service.authorizeUpload(token)
        val temporary = fileIO { Files.createTempFile("zero-upload-", ".part") }
        try {
            var name: String? = null
            var category: String? = null
            var content: String? = null
            call.receiveMultipart(formFieldLimit = multipartLimit).forEachPart { part ->
                try {
                    when (part) {
                        is PartData.FileItem -> if (part.name == "file") {
                            if (name != null) invalid("每次只能上传一个文件")
                            name = safeFileName(part.originalFileName)
                            content = part.contentType?.let { "${it.contentType}/${it.contentSubtype}" }?.takeIf { it.length <= 255 }
                            part.provider().copyAndClose(temporary.toFile().writeChannel())
                        }
                        is PartData.FormItem -> if (part.name == "bizType") {
                            category = part.value.trim().ifEmpty { null }?.let(::fileBusinessCode)
                        }
                        else -> Unit
                    }
                } finally {
                    part.release()
                }
            }
            val filename = name ?: invalid("缺少文件")
            val bytes = fileIO { Files.size(temporary) }
            if (bytes > fileLimit) throw SystemFailure(413, "上传超过 50 MiB")
            call.respond(HttpStatusCode.Created, service.publish(token, temporary, filename, category, content, bytes))
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { runCatching { Files.deleteIfExists(temporary) } }
        }
    }
    get("{id}/download") {
        val download = service.open(call.bearer(), call.id())
        val name = HexFormat.of().withPrefix("%").withUpperCase().formatHex(download.entry.fileName.toByteArray())
        try {
            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename*=UTF-8''$name")
            call.response.header("X-Content-Type-Options", "nosniff")
            call.response.header(HttpHeaders.ContentLength, download.entry.sizeBytes.toString())
            call.respondOutputStream(ContentType.Application.OctetStream) {
                withContext(Dispatchers.IO) { download.input.use { it.copyTo(this@respondOutputStream) } }
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { runCatching { download.input.close() } }
            throw failure
        }
    }
    delete("{id}") {
        service.delete(call.bearer(), call.id())
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun safeFileName(value: String?): String =
    value.orEmpty().substringAfterLast('/').substringAfterLast('\\').trim()
        .takeIf { it.length in 1..255 && it !in setOf(".", "..") && it.none { ch -> Character.isISOControl(ch) } }
        ?: invalid("文件名无效")
