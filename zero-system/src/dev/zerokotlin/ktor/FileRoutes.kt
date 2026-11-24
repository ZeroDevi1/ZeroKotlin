package dev.zerokotlin.ktor

import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.core.storage.FileStorage
import dev.zerokotlin.core.storage.FileStorageBackend
import dev.zerokotlin.core.storage.FileStorages
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
import io.ktor.utils.io.copyAndClose
import io.ktor.util.cio.writeChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.ResultSet
import java.util.HexFormat
import java.util.UUID

private const val fileRead = "system:file:read"
private const val fileUpload = "system:file:upload"
private const val fileDownload = "system:file:download"
private const val fileDelete = "system:file:delete"
private const val fileLimit = 50L * 1024 * 1024
// 总请求上限额外留出 multipart 边界和表单字段的空间。
internal const val multipartLimit = 51L * 1024 * 1024

@Serializable
private data class StoredFile(
    val id: String, val fileName: String, val bizType: String?, val contentType: String?, val sizeBytes: Long,
    val storageBackend: String, val state: String, val uploadedBy: String, val createdAt: Long,
)

private data class FileRow(val entry: StoredFile, val location: String)

internal fun Route.fileRoutes(store: SystemStore, files: FileStorages) = route("files") {
    get {
        val token = call.bearer()
        val window = call.window()
        val query = call.request.queryParameters
        val name = query["name"]?.trim()?.ifEmpty { null }
        val category = query["bizType"]?.trim()?.ifEmpty { null }?.let(::businessCode)
        val filters = buildList {
            if (name != null) {
                if (name.length > 255 || name.any { Character.isISOControl(it) }) invalid("文件名筛选无效")
                val escaped = name.replace("!", "!!").replace("%", "!%").replace("_", "!_")
                add("file_name LIKE ? ESCAPE '!'" to "%$escaped%")
            }
            if (category != null) add("biz_type = ?" to category)
        }
        val where = if (filters.isEmpty()) "" else filters.joinToString(" AND ", prefix = " WHERE ") { it.first }
        val parameters = filters.map { it.second }.toTypedArray()
        call.respond(store.read(token, fileRead) { db, _ ->
            val count = db.rows("SELECT COUNT(*) FROM sys_file$where", *parameters) { it.getLong(1) }.single()
            val entries = db.rows(
                "SELECT * FROM sys_file$where ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                *parameters, window.size, window.offset, map = ::fileRow,
            ).map { it.entry }
            window.result(entries, count)
        })
    }
    post {
        val token = call.bearer()
        store.read(token, fileUpload) { _, _ -> Unit }
        val temporary = disk { Files.createTempFile("zero-upload-", ".part") }
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
                            category = part.value.trim().ifEmpty { null }?.let(::businessCode)
                        }
                        else -> Unit
                    }
                } finally {
                    part.release()
                }
            }
            val filename = name ?: invalid("缺少文件")
            val bytes = disk { Files.size(temporary) }
            if (bytes > fileLimit) throw SystemFailure(413, "上传超过 50 MiB")
            call.respond(HttpStatusCode.Created, publishFile(store, files, token, temporary, filename, category, content, bytes))
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { runCatching { Files.deleteIfExists(temporary) } }
        }
    }
    get("{id}/download") {
        val token = call.bearer()
        val id = fileId(call.id())
        val row = store.read(token, fileDownload) { db, _ ->
            db.file(id)?.takeIf { it.entry.state == "AVAILABLE" } ?: missing("可下载的文件")
        }
        val storage = files.resolveRow(row)
        store.write(token, fileDownload) { db, _, audit ->
            if (db.file(id)?.entry?.state != "AVAILABLE") missing("可下载的文件")
            audit("file.download", id)
        }
        val name = HexFormat.of().withPrefix("%").withUpperCase().formatHex(row.entry.fileName.toByteArray())
        val input = disk { storage.open(UUID.fromString(id)) } ?: missing("文件内容")
        try {
            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename*=UTF-8''$name")
            call.response.header("X-Content-Type-Options", "nosniff")
            call.response.header(HttpHeaders.ContentLength, row.entry.sizeBytes.toString())
            call.respondOutputStream(ContentType.Application.OctetStream) {
                withContext(Dispatchers.IO) { input.use { it.copyTo(this@respondOutputStream) } }
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { runCatching { input.close() } }
            throw failure
        }
    }
    delete("{id}") {
        val token = call.bearer()
        val id = fileId(call.id())
        val row = store.write(token, fileDelete) { db, _, _ ->
            val current = db.file(id) ?: return@write null
            if (current.entry.state == "UPLOADING") throw SystemFailure(409, "文件正在上传")
            files.resolveRow(current)
            db.change("UPDATE sys_file SET state = 'DELETING' WHERE id = ?", id)
            current
        }
        // 删除对象失败时保留 DELETING，重试可沿用原对象位置。
        if (row != null) {
            disk { files.resolveRow(row).delete(UUID.fromString(id)) }
            store.write(token, fileDelete) { db, _, audit ->
                if (db.change("DELETE FROM sys_file WHERE id = ? AND state = 'DELETING'", id) == 1) audit("file.delete", id)
            }
        }
        call.respond(HttpStatusCode.NoContent)
    }
}

private suspend fun publishFile(
    store: SystemStore, files: FileStorages, token: String, temporary: Path, name: String,
    category: String?, content: String?, bytes: Long,
): StoredFile {
    val id = newId()
    val key = UUID.fromString(id)
    val storage = files.default
    val entry = store.write(token, fileUpload) { db, actor, _ ->
        val now = System.currentTimeMillis()
        db.change(
            """INSERT INTO sys_file
               (id, file_name, biz_type, content_type, size_bytes, storage_backend, storage_location_id, state, uploaded_by, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, 'UPLOADING', ?, ?)""",
            id, name, category, content, bytes, storage.backend.name, storage.locationId, actor.id, now,
        )
        StoredFile(id, name, category, content, bytes, storage.backend.name, "AVAILABLE", actor.id, now)
    }
    // SQL 与对象存储不能共用事务；先记录 UPLOADING，上传完成后才对下载可见。
    return withContext(NonCancellable) {
        try {
            disk { Files.newInputStream(temporary).use { storage.put(key, it, bytes) } }
            store.write(token, fileUpload) { db, _, audit ->
                check(db.change("UPDATE sys_file SET state = 'AVAILABLE' WHERE id = ? AND state = 'UPLOADING'", id) == 1)
                audit("file.upload", id)
            }
            entry
        } catch (failure: Exception) {
            try {
                store.discardFileUpload(id) { disk { storage.delete(key) } }
            } catch (cleanup: Exception) {
                failure.addSuppressed(cleanup)
            }
            throw failure
        }
    }
}

private fun fileId(raw: String): String = try {
    UUID.fromString(raw).toString().takeIf { it.equals(raw, ignoreCase = true) } ?: invalid("文件 ID 无效")
} catch (_: IllegalArgumentException) {
    invalid("文件 ID 无效")
}

private fun safeFileName(value: String?): String =
    value.orEmpty().substringAfterLast('/').substringAfterLast('\\').trim()
        .takeIf { it.length in 1..255 && it !in setOf(".", "..") && it.none { ch -> Character.isISOControl(ch) } }
        ?: invalid("文件名无效")

private fun businessCode(value: String): String = value.takeIf(permissionPattern::matches) ?: invalid("业务分类编码无效")

private fun Connection.file(id: String): FileRow? = rows("SELECT * FROM sys_file WHERE id = ?", id, map = ::fileRow).singleOrNull()

private fun fileRow(row: ResultSet): FileRow = FileRow(
    StoredFile(
        row.getString("id"), row.getString("file_name"), row.getString("biz_type"), row.getString("content_type"),
        row.getLong("size_bytes"), row.getString("storage_backend"), row.getString("state"), row.getString("uploaded_by"),
        row.getLong("created_at"),
    ), row.getString("storage_location_id"),
)

private fun FileStorages.resolveRow(row: FileRow): FileStorage = try {
    resolve(FileStorageBackend.valueOf(row.entry.storageBackend), row.location)
} catch (_: Exception) {
    throw SystemFailure(503, "未配置该文件的原存储位置")
}

private suspend fun <T> disk(action: () -> T): T = withContext(Dispatchers.IO) {
    try { action() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: SystemFailure) { throw failure }
    catch (_: Exception) { throw SystemFailure(503, "文件存储暂不可用") }
}
