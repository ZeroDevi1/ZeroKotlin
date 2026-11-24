package dev.zerokotlin.ktor.system.file

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.plugins.pagination.Page as MybatisPage
import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.core.storage.FileStorage
import dev.zerokotlin.core.storage.FileStorageBackend
import dev.zerokotlin.core.storage.FileStorages
import dev.zerokotlin.ktor.Page
import dev.zerokotlin.ktor.SystemStore
import dev.zerokotlin.ktor.Window
import dev.zerokotlin.ktor.invalid
import dev.zerokotlin.ktor.missing
import dev.zerokotlin.ktor.newId
import dev.zerokotlin.ktor.permissionPattern
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
internal data class StoredFile(
    val id: String, val fileName: String, val bizType: String?, val contentType: String?, val sizeBytes: Long,
    val storageBackend: String, val state: String, val uploadedBy: String, val createdAt: Long,
)

/** Controller 负责关闭打开的对象流，包括生成 HTTP 响应失败的情况。 */
internal data class FileDownload(val entry: StoredFile, val input: InputStream)

/** 对象存储在事务外执行。UPLOADING 和 DELETING 记录供重试及补偿流程复核。 */
internal class FileService(private val store: SystemStore, private val files: FileStorages) {
    private val read = "system:file:read"
    private val upload = "system:file:upload"
    private val download = "system:file:download"
    private val delete = "system:file:delete"

    suspend fun list(token: String, window: Window, name: String?, category: String?): Page<StoredFile> {
        val query = QueryWrapper<FileRecord>().orderByDesc("created_at", "id")
        if (name != null) {
            if (name.length > 255 || name.any { Character.isISOControl(it) }) invalid("文件名筛选无效")
            val escaped = name.replace("!", "!!").replace("%", "!%").replace("_", "!_")
            query.apply("file_name LIKE {0} ESCAPE '!'", "%$escaped%")
        }
        if (category != null) query.eq("biz_type", category)
        return store.read(token, read) { db, _ ->
            mapper(db) { records ->
                val page = records.selectPage(MybatisPage<FileRecord>(window.page.toLong(), window.size.toLong()), query)
                window.result(page.records.map(FileRecord::summary), page.total)
            }
        }
    }

    suspend fun authorizeUpload(token: String) {
        store.read(token, upload) { _, _ -> Unit }
    }

    /** 保存元数据后上传对象。上传失败则按原对象位置补偿。 */
    suspend fun publish(token: String, temporary: Path, name: String, category: String?, content: String?, bytes: Long): StoredFile {
        val id = newId()
        val key = UUID.fromString(id)
        val storage = files.default
        val entry = store.write(token, upload) { db, actor, _ ->
            val record = FileRecord().also {
                it.id = id
                it.fileName = name
                it.bizType = category
                it.contentType = content
                it.sizeBytes = bytes
                it.storageBackend = storage.backend.name
                it.storageLocationId = storage.locationId
                it.state = "UPLOADING"
                it.uploadedBy = actor.id
                it.createdAt = System.currentTimeMillis()
            }
            check(mapper(db) { it.insert(record) } == 1)
            record.summary().copy(state = "AVAILABLE")
        }
        return withContext(NonCancellable) {
            try {
                fileIO { Files.newInputStream(temporary).use { storage.put(key, it, bytes) } }
                store.write(token, upload) { db, _, audit ->
                    check(mapper(db) { it.updateState(id, "AVAILABLE", "UPLOADING") } == 1)
                    audit("file.upload", id)
                }
                entry
            } catch (failure: Exception) {
                try {
                    discardUpload(id, storage, key)
                } catch (cleanup: Exception) {
                    failure.addSuppressed(cleanup)
                }
                throw failure
            }
        }
    }

    /** 复核 AVAILABLE 并记录下载授权，再打开对象流。 */
    suspend fun open(token: String, rawId: String): FileDownload {
        val id = fileId(rawId)
        val record = store.read(token, download) { db, _ ->
            mapper(db) { it.selectById(id) }?.takeIf { it.state == "AVAILABLE" } ?: missing("可下载的文件")
        }
        val storage = resolve(record)
        store.write(token, download) { db, _, audit ->
            if (mapper(db) { it.selectById(id) }?.state != "AVAILABLE") missing("可下载的文件")
            audit("file.download", id)
        }
        val input = fileIO { storage.open(UUID.fromString(id)) } ?: missing("文件内容")
        return FileDownload(record.summary(), input)
    }

    /** 存储失败保留 DELETING，后续请求沿用原对象位置重试。 */
    suspend fun delete(token: String, rawId: String) {
        val id = fileId(rawId)
        val storage = store.write(token, delete) { db, _, _ ->
            mapper(db) { records ->
                val current = records.selectById(id) ?: return@mapper null
                if (current.state == "UPLOADING") throw SystemFailure(409, "文件正在上传")
                resolve(current).also { records.updateState(id, "DELETING") }
            }
        } ?: return
        fileIO { storage.delete(UUID.fromString(id)) }
        store.write(token, delete) { db, _, audit ->
            if (deleteRecord(db, id) == 1) audit("file.delete", id)
        }
    }

    private suspend fun discardUpload(id: String, storage: FileStorage, key: UUID) {
        val owned = withContext(Dispatchers.IO) {
            store.transaction(write = true) { db -> mapper(db) { it.updateState(id, "DELETING", "UPLOADING") } == 1 }
        }
        if (!owned) return
        fileIO { storage.delete(key) }
        withContext(Dispatchers.IO) { store.transaction(write = true) { db -> deleteRecord(db, id) } }
    }

    private fun deleteRecord(db: Connection, id: String): Int =
        mapper(db) { it.delete(QueryWrapper<FileRecord>().eq("id", id).eq("state", "DELETING")) }

    private fun resolve(row: FileRecord): FileStorage = try {
        files.resolve(FileStorageBackend.valueOf(checkNotNull(row.storageBackend)), checkNotNull(row.storageLocationId))
    } catch (_: Exception) {
        throw SystemFailure(503, "未配置该文件的原存储位置")
    }

    private fun <T> mapper(db: Connection, action: (FileMapper) -> T): T =
        store.mappers.withSession(db) { action(it.getMapper(FileMapper::class.java)) }
}

private fun fileId(raw: String): String = try {
    UUID.fromString(raw).toString().takeIf { it.equals(raw, ignoreCase = true) } ?: invalid("文件 ID 无效")
} catch (_: IllegalArgumentException) {
    invalid("文件 ID 无效")
}

internal fun fileBusinessCode(value: String): String =
    value.takeIf(permissionPattern::matches) ?: invalid("业务分类编码无效")

internal suspend fun <T> fileIO(action: () -> T): T = withContext(Dispatchers.IO) {
    try { action() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: SystemFailure) { throw failure }
    catch (_: Exception) { throw SystemFailure(503, "文件存储暂不可用") }
}
