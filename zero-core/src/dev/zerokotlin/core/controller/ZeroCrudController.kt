package dev.zerokotlin.core.controller

import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import com.baomidou.mybatisplus.spring.service.IService
import dev.zerokotlin.core.OperationAudit
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.core.service.REPORT_ROWS
import dev.zerokotlin.core.service.XLSX_MEDIA_TYPE
import dev.zerokotlin.core.service.XlsxReport
import io.swagger.v3.oas.annotations.media.Schema
import java.io.Serializable
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.UncategorizedSQLException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.util.MultiValueMap
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException

/** 新增成功后的资源主键。 */
data class CrudCreated(@field:Schema(description = "新记录的主键。") val id: Serializable)

/** 一次成功导入的记录数。 */
data class CrudImportResult(@field:Schema(description = "成功写入的记录数。") val imported: Int)

/**
 * 为业务控制器提供基于 MyBatis-Plus 的默认操作。
 *
 * 子类选择要发布的路由，在入口声明权限并按需要转换请求、响应或调用自定义业务逻辑。
 */
abstract class ZeroCrudController<T : Any>(
    private val crudService: IService<T>,
    entityType: Class<T>,
    transactionManager: PlatformTransactionManager,
    private val audit: OperationAudit,
) {
    private val transactions = TransactionTemplate(transactionManager)
    private val reads = TransactionTemplate(transactionManager).apply { isReadOnly = true }
    private val query by lazy { CrudQuery(entityType) }

    /** 按允许的等值条件查询，按主键升序分页。 */
    protected fun queryPage(window: PageWindow, parameters: MultiValueMap<String, String>): PageResult<T> =
        checkNotNull(reads.execute {
            val page = crudService.page(Page<T>(window.page.toLong(), window.size.toLong()), query.build(parameters))
            window.result(page.records, page.total)
        })

    /** 按主键查询，缺失时返回 HTTP 404。 */
    protected fun queryById(id: Serializable): T = crudService.getById(id) ?: missing()

    /** 插入记录并记录审计，返回 MyBatis-Plus 回填或调用方提供的主键。 */
    protected fun add(record: T, permission: String): CrudCreated = checkNotNull(transactions.execute {
        check(crudService.save(record)) { "插入记录失败" }
        val id = query.table.getPropertyValue(record, query.table.keyProperty) as? Serializable
            ?: error("主键为空或不可序列化")
        audit.record(permission, target(id))
        CrudCreated(id)
    })

    /** 按路径主键更新并记录审计，可更新字段以实体的 MyBatis-Plus 策略为准。 */
    protected fun edit(id: Serializable, record: T, permission: String) {
        val targetId = target(id)
        transactions.executeWithoutResult {
            query.table.setPropertyValue(record, query.table.keyProperty, id)
            if (!crudService.updateById(record)) queryById(id)
            audit.record(permission, targetId)
        }
    }

    /** 按主键删除并记录审计，删除策略取自实体映射。 */
    protected fun delete(id: Serializable, permission: String) {
        val targetId = target(id)
        transactions.executeWithoutResult {
            if (!crudService.removeById(id)) missing()
            audit.record(permission, targetId)
        }
    }

    /** 下载当前筛选条件下的 XLSX 报表，调用方提供公开报表模型的投影。 */
    protected fun <R : Any> exportXls(
        filename: String,
        parameters: MultiValueMap<String, String>,
        report: XlsxReport<R>,
        project: (T) -> R,
    ): ResponseEntity<ByteArray> {
        val records = crudService.page(Page<T>(1, REPORT_ROWS + 1L, false), query.build(parameters)).records
        if (records.size > REPORT_ROWS) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "最多导出 $REPORT_ROWS 条记录，请缩小筛选范围")
        }
        return download(filename, report.write(records.map(project)))
    }

    /** 下载与上传字段一致的空白模板。 */
    protected fun templateXls(filename: String, report: XlsxReport<*>): ResponseEntity<ByteArray> =
        download(filename, report.template())

    /** 解析整个工作簿后在一个事务内调用创建逻辑，每行失败会回滚整批。 */
    protected fun <R : Any> importXls(
        file: MultipartFile,
        report: XlsxReport<R>,
        factory: () -> R,
        create: (R) -> Unit,
    ): CrudImportResult {
        val rows = report.read(file, factory)
        transactions.executeWithoutResult {
            for ((number, value) in rows) {
                try {
                    create(value)
                } catch (error: DataAccessException) {
                    throw importConstraintFailure(number, error)
                } catch (error: ResponseStatusException) {
                    if (error.statusCode != HttpStatus.BAD_REQUEST && error.statusCode != HttpStatus.CONFLICT) throw error
                    throw ResponseStatusException(error.statusCode, "第 $number 行：${error.reason ?: "数据无效"}")
                }
            }
        }
        return CrudImportResult(rows.size)
    }

    private fun importConstraintFailure(number: Int, error: DataAccessException): ResponseStatusException {
        // SQLite JDBC 不提供 SQLState，SQLException.errorCode 只保留主错误码。
        val sqliteError = (error as? UncategorizedSQLException)?.sqlException as? SQLiteException
        val duplicate = when (sqliteError?.resultCode) {
            SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE,
            SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY,
            SQLiteErrorCode.SQLITE_CONSTRAINT_ROWID -> true
            else -> error is DuplicateKeyException
        }
        return when {
            duplicate -> ResponseStatusException(HttpStatus.CONFLICT, "第 $number 行：记录已存在")
            error is DataIntegrityViolationException || sqliteError?.errorCode == SQLiteErrorCode.SQLITE_CONSTRAINT.code ->
                ResponseStatusException(HttpStatus.BAD_REQUEST, "第 $number 行：数据不符合数据库约束")
            else -> throw error
        }
    }

    private fun download(filename: String, data: ByteArray): ResponseEntity<ByteArray> = ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
        .cacheControl(CacheControl.noStore())
        .headers { it.contentDisposition = ContentDisposition.attachment().filename(filename, Charsets.UTF_8).build() }
        .body(data)

    private fun target(id: Serializable): String = id.toString().also {
        if (it.isBlank() || it.length > 120 || it == "." || it == ".." || it.any { char -> char.isISOControl() || char in "/\\;%" }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "主键须为长度不超过 120 的合法路径段")
        }
    }

    private fun missing(): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在")
}
