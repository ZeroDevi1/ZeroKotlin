package dev.zerokotlin.system.service

import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.core.storage.FileStorageBackend
import dev.zerokotlin.core.storage.FileStorages
import dev.zerokotlin.system.dto.FileEntry
import dev.zerokotlin.system.entity.FileRecord
import dev.zerokotlin.system.entity.FileState
import dev.zerokotlin.system.entity.FileState.AVAILABLE
import dev.zerokotlin.system.entity.FileState.DELETING
import dev.zerokotlin.system.entity.FileState.UPLOADING
import dev.zerokotlin.system.mapper.FileMapper
import dev.zerokotlin.system.security.FILE_DELETE
import dev.zerokotlin.system.security.FILE_DOWNLOAD
import dev.zerokotlin.system.security.FILE_READ
import dev.zerokotlin.system.security.FILE_UPLOAD
import dev.zerokotlin.system.security.SystemActor
import dev.zerokotlin.system.security.currentSystemActor
import dev.zerokotlin.system.support.checkedCode
import dev.zerokotlin.system.support.checkedText
import dev.zerokotlin.system.support.conflict
import dev.zerokotlin.system.support.invalid
import dev.zerokotlin.system.support.missing
import dev.zerokotlin.system.support.newId
import dev.zerokotlin.system.support.query
import java.io.InputStream
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.InvalidMediaTypeException
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

/** 管理文件元数据、存储内容与操作审计。存储 I/O 在数据库事务之外执行。 */
@Service
class SystemFiles(
    private val files: FileMapper,
    private val storages: FileStorages,
    private val writes: SystemWrites,
    transactionManager: PlatformTransactionManager,
) {
    private val transactions = TransactionTemplate(transactionManager)
    private val logger = LoggerFactory.getLogger(javaClass)

    /** 按文件名片段和精确业务分类分页查询，按登记时间和 ID 倒序。 */
    @Transactional(readOnly = true)
    fun list(window: PageWindow, name: String?, bizType: String?): PageResult<FileEntry> {
        currentSystemActor().requireScope(FILE_READ)
        val filters = query<FileRecord>().orderByDesc("created_at", "id")
        if (!name.isNullOrBlank()) {
            if (name.any { it.isISOControl() }) invalid("文件名筛选包含控制字符")
            val pattern = checkedText(name, "文件名", 255).replace("!", "!!").replace("%", "!%").replace("_", "!_")
            filters.apply("file_name LIKE {0} ESCAPE '!'", "%$pattern%")
        }
        if (!bizType.isNullOrBlank()) filters.eq("biz_type", checkedCode(bizType.trim()))
        val page = files.selectPage(Page<FileRecord>(window.page.toLong(), window.size.toLong()), filters)
        return window.result(page.records.map { it.toEntry() }, page.total)
    }

    /** 登记上传意图，写入对象后发布文件并记录审计。失败时尝试回收未发布的对象。 */
    @Transactional(propagation = Propagation.NEVER)
    fun upload(file: MultipartFile, bizType: String?): FileEntry {
        val storage = storages.default
        val name = file.originalFilename.orEmpty().substringAfterLast('/').substringAfterLast('\\')
        if (name.any { it.isISOControl() }) invalid("文件名无效")
        val fileName = checkedText(name, "文件名", 255)
        if (fileName == "." || fileName == "..") invalid("文件名无效")
        val record = FileRecord(
            id = newId(),
            fileName = fileName,
            bizType = bizType?.trim()?.takeIf(String::isNotEmpty)?.let(::checkedCode),
            contentType = mediaTypeOf(file.contentType),
            sizeBytes = file.size,
            storageBackend = storage.backend.name,
            storageLocationId = storage.locationId,
            createdAt = System.currentTimeMillis(),
        )

        transactions.executeWithoutResult {
            record.uploadedBy = writes.authorize(FILE_UPLOAD).user.id
            check(files.insert(record) == 1) { "未登记上传意图" }
        }
        try {
            storageOperation("上传", record.id) {
                file.inputStream.use { storage.put(UUID.fromString(record.id), it, record.sizeBytes) }
            }
            transactions.executeWithoutResult {
                val actor = writes.authorize(FILE_UPLOAD)
                check(files.changeState(record.id, UPLOADING.name, AVAILABLE.name) == 1) { "上传状态已变更" }
                writes.record(actor, "file.upload", record.id)
            }
        } catch (failure: Exception) {
            try {
                val abandoned = transactions.execute { files.changeState(record.id, UPLOADING.name, DELETING.name) == 1 }
                if (abandoned == true) remove(record, null)
            } catch (cleanupFailure: Exception) {
                logger.warn("File upload cleanup pending for {} ({})", record.id, cleanupFailure.javaClass.simpleName)
            }
            throw failure
        }
        record.state = AVAILABLE.name
        return record.toEntry()
    }

    /** 打开可用对象，提交下载审计后将流交给调用方，回调结束时关闭流。 */
    @Transactional(propagation = Propagation.NEVER)
    fun download(id: UUID, consume: (FileEntry, InputStream) -> Unit) {
        currentSystemActor().requireScope(FILE_DOWNLOAD)
        val record = availableFile(id.toString())
        val input = storageOperation("下载", record.id) { storageOf(record).open(id) } ?: missing("文件内容")
        input.use {
            transactions.executeWithoutResult {
                val actor = writes.authorize(FILE_DOWNLOAD)
                availableFile(record.id)
                writes.record(actor, "file.download", record.id)
            }
            consume(record.toEntry(), it)
        }
    }

    /** 禁止新下载，再清理对象和元数据。存储失败时保留删除状态供授权用户重试。 */
    @Transactional(propagation = Propagation.NEVER)
    fun delete(id: UUID) {
        val (record, actor) = transactions.execute {
            val actor = writes.authorize(FILE_DELETE)
            val record = files.selectById(id.toString()) ?: return@execute null
            when (FileState.valueOf(record.state)) {
                UPLOADING -> conflict("文件正在上传")
                AVAILABLE -> check(files.changeState(record.id, AVAILABLE.name, DELETING.name) == 1) {
                    "文件状态已变更"
                }
                DELETING -> Unit
            }
            record to actor
        } ?: return
        remove(record, actor)
    }

    private fun availableFile(id: String): FileRecord =
        files.selectById(id)?.takeIf { it.state == AVAILABLE.name } ?: missing("可下载的文件")

    private fun storageOf(record: FileRecord) =
        storages.resolve(FileStorageBackend.valueOf(record.storageBackend), record.storageLocationId)

    private fun remove(record: FileRecord, actor: SystemActor?) {
        storageOperation("删除", record.id) { storageOf(record).delete(UUID.fromString(record.id)) }
        transactions.executeWithoutResult {
            if (files.finishDelete(record.id) == 1 && actor != null) writes.record(actor, "file.delete", record.id)
        }
    }

    private fun <T> storageOperation(action: String, id: String, operate: () -> T): T = try {
        operate()
    } catch (failure: Exception) {
        logger.warn("File storage {} failed for {} ({})", action, id, failure.javaClass.simpleName)
        throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "文件存储暂不可用")
    }

    private fun mediaTypeOf(value: String?): String? = try {
        value?.let(MediaType::parseMediaType)?.let { "${it.type}/${it.subtype}" }?.takeIf { it.length <= 255 }
    } catch (_: InvalidMediaTypeException) {
        null
    }

    private fun FileRecord.toEntry() =
        FileEntry(id, fileName, bizType, contentType, sizeBytes, storageBackend, state, uploadedBy, createdAt)
}
