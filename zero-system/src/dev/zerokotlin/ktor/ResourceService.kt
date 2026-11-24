package dev.zerokotlin.ktor

import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.core.excel.XlsxColumn
import dev.zerokotlin.core.excel.XlsxRow
import dev.zerokotlin.core.excel.XlsxWorkbooks
import dev.zerokotlin.ktor.system.auth.CurrentUser
import java.sql.Connection
import java.sql.SQLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException

/** 同一业务事务中的存储接口。筛选、更新和删除都使用业务层传入的数据范围。 */
internal interface ResourceMapper {
    fun list(db: Connection, filters: ResourceFields, window: Window): Page<JsonObject>
    fun export(db: Connection, filters: ResourceFields): List<JsonObject>
    fun find(db: Connection, id: Any, scope: ResourceFields): JsonObject?
    fun create(db: Connection, values: ResourceFields): JsonPrimitive
    fun update(db: Connection, id: Any, values: ResourceFields, scope: ResourceFields): Int
    fun delete(db: Connection, id: Any, scope: ResourceFields): Int
}

/** Controller 依赖此契约，各业务 Service 可在此基础上添加自有操作。 */
internal interface ResourceService {
    val acceptsCreateBody: Boolean
    val supportsUpdate: Boolean
    val importColumns: List<XlsxColumn>

    suspend fun list(token: String, window: Window, query: Map<String, List<String>>): Page<JsonObject>
    suspend fun find(token: String, rawId: String): JsonObject
    suspend fun create(token: String, body: JsonObject): JsonObject
    suspend fun update(token: String, rawId: String, body: JsonObject)
    suspend fun delete(token: String, rawId: String)
    suspend fun export(token: String, query: Map<String, List<String>>): ByteArray
    suspend fun authorizeImport(token: String)
    suspend fun importRows(token: String, rows: List<XlsxRow>)
}

/** 保持权限、验证、CRUD 和 XLSX 入口一致，写操作在一个事务中保存记录及审计。 */
internal open class DefaultResourceService(
    private val store: SystemStore,
    private val spec: ResourceSpec,
    private val mapper: ResourceMapper,
) : ResourceService {
    final override val acceptsCreateBody = spec.create.isNotEmpty()
    final override val supportsUpdate = spec.update.isNotEmpty()
    final override val importColumns = spec.excelCreate.map {
        XlsxColumn(it.heading, 32_767, it.kind !in setOf(ResourceKind.INT, ResourceKind.BOOLEAN))
    }

    private val readPermission = "${spec.authority}:read"
    private val createPermission = "${spec.authority}:create"
    private val updatePermission = "${spec.authority}:update"
    private val deletePermission = "${spec.authority}:delete"

    /** 将归属、租户等条件传到实际 SQL。列表、详情、导出、更新和删除共用此入口。 */
    protected open fun rowScope(actor: CurrentUser, permission: String): ResourceFields = emptyList()

    /** 创建与 Excel 导入共同应用此规则，可由服务端设置归属列。 */
    protected open fun prepareCreate(actor: CurrentUser, values: ResourceFields): ResourceFields = values

    override suspend fun list(token: String, window: Window, query: Map<String, List<String>>): Page<JsonObject> {
        val filters = spec.filters(query)
        return store.read(token, readPermission) { db, actor ->
            mapper.list(db, filters + rowScope(actor, readPermission), window)
        }
    }

    override suspend fun find(token: String, rawId: String): JsonObject {
        val id = spec.pathId(rawId)
        return store.read(token, readPermission) { db, actor ->
            mapper.find(db, id, rowScope(actor, readPermission)) ?: missing("记录")
        }
    }

    override suspend fun create(token: String, body: JsonObject): JsonObject {
        val values = spec.values(body, updating = false)
        return store.write(token, createPermission) { db, actor, audit ->
            val id = insert(db, actor, values)
            audit(createPermission, checkedTarget(id))
            buildJsonObject { put("id", id) }
        }
    }

    override suspend fun update(token: String, rawId: String, body: JsonObject) {
        val id = spec.pathId(rawId)
        val values = spec.values(body, updating = true)
        store.write(token, updatePermission) { db, actor, audit ->
            val scope = rowScope(actor, updatePermission)
            val changed = resourceConstraint { mapper.update(db, id, values, scope) }
            if (changed == 0 && mapper.find(db, id, scope) == null) missing("记录")
            audit(updatePermission, checkedTarget(id))
        }
    }

    override suspend fun delete(token: String, rawId: String) {
        val id = spec.pathId(rawId)
        store.write(token, deletePermission) { db, actor, audit ->
            val changed = resourceConstraint { mapper.delete(db, id, rowScope(actor, deletePermission)) }
            if (changed == 0) missing("记录")
            audit(deletePermission, checkedTarget(id))
        }
    }

    override suspend fun export(token: String, query: Map<String, List<String>>): ByteArray {
        val filters = spec.filters(query)
        val rows = store.read(token, readPermission) { db, actor ->
            mapper.export(db, filters + rowScope(actor, readPermission))
        }
        if (rows.size > 1000) invalid("最多导出 1000 条记录，请缩小筛选范围")
        return withContext(Dispatchers.IO) {
            XlsxWorkbooks.write(spec.excelExport.map { it.heading }, rows.map { row ->
                spec.excelExport.map { column ->
                    val value = row[column.property] as? JsonPrimitive
                    when {
                        value == null || value == JsonNull -> null
                        column.kind == ResourceKind.BOOLEAN -> value.content == "true"
                        column.kind == ResourceKind.INT -> value.content.toInt()
                        else -> value.content
                    }
                }
            })
        }
    }

    override suspend fun authorizeImport(token: String) {
        store.read(token, createPermission) { _, _ -> Unit }
    }

    /** 一批中的任一行失败都会回滚整个导入，错误信息保留 Excel 行号。 */
    override suspend fun importRows(token: String, rows: List<XlsxRow>) {
        store.write(token, createPermission) { db, actor, audit ->
            for (row in rows) {
                try {
                    val body = buildJsonObject {
                        spec.excelCreate.zip(row.cells).forEach { (column, value) ->
                            if (value.isNotEmpty()) put(column.property, JsonPrimitive(value))
                        }
                    }
                    val id = insert(db, actor, spec.values(body, updating = false))
                    audit(createPermission, checkedTarget(id))
                } catch (failure: SystemFailure) {
                    throw SystemFailure(failure.status, "第 ${row.number} 行：${failure.message}")
                }
            }
        }
    }

    private fun insert(db: Connection, actor: CurrentUser, values: ResourceFields): JsonPrimitive =
        resourceConstraint { mapper.create(db, prepareCreate(actor, values)) }
}

/** JDBC 和 MyBatis 的包装异常沿用同一 HTTP 错误语义，避免返回驱动的 SQL 和参数。 */
private inline fun <T> resourceConstraint(action: () -> T): T = try {
    action()
} catch (failure: Exception) {
    val sql = generateSequence<Throwable>(failure) { it.cause }.filterIsInstance<SQLException>().firstOrNull()
        ?: throw failure
    val sqlite = (sql as? SQLiteException)?.resultCode
    when {
        sql.sqlState == "23505" || sql.errorCode == 1062 ||
            sqlite in setOf(SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY, SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE, SQLiteErrorCode.SQLITE_CONSTRAINT_ROWID) ->
            throw SystemFailure(409, "记录已存在")
        sql.sqlState?.startsWith("23") == true || sqlite?.code?.and(0xff) == SQLiteErrorCode.SQLITE_CONSTRAINT.code ->
            invalid("数据不符合数据库约束")
        else -> throw failure
    }
}
