package dev.zerokotlin.ktor

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException
import java.math.BigDecimal
import java.math.BigInteger
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Types
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID

/** SQL 标识符由生成器提供；请求参数只作为绑定值，不拼接进 SQL。 */
internal data class ResourceSpec(
    val path: String,
    val table: String,
    val mysql: Boolean,
    val authority: String,
    val columns: List<ResourceColumn>,
) {
    val key = columns.first()
    val create = columns.filterNot { it.identity || it.computed }
    val update = create.filterNot { it == key }
    val excelCreate = create.filter { it.kind != ResourceKind.BYTES }.takeUnless {
        create.any { it.kind == ResourceKind.BYTES && required(it) }
    }.orEmpty()
    val excelExport = columns.filter { it.kind != ResourceKind.BYTES }
    val selected = columns.joinToString(", ") { it.sql }
    fun required(column: ResourceColumn) = column == key || !column.nullable && !column.defaulted
}

internal data class ResourceColumn(
    val property: String,
    val sql: String,
    val heading: String,
    val kind: ResourceKind,
    val nullable: Boolean,
    val identity: Boolean,
    val computed: Boolean,
    val defaulted: Boolean,
)

internal enum class ResourceKind(private val jdbc: Int) {
    BOOLEAN(Types.BOOLEAN), INT(Types.INTEGER), LONG(Types.BIGINT), BIG_INTEGER(Types.DECIMAL),
    FLOAT(Types.REAL), DOUBLE(Types.DOUBLE), DECIMAL(Types.DECIMAL), STRING(Types.VARCHAR),
    BYTES(Types.VARBINARY), DATE(Types.DATE), TIME(Types.TIME), TIMESTAMP(Types.TIMESTAMP),
    TIMESTAMP_TZ(Types.TIMESTAMP_WITH_TIMEZONE), UUID(Types.OTHER);

    fun parse(value: String): Any = when (this) {
        BOOLEAN -> when (value.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> throw IllegalArgumentException()
        }
        INT -> value.toInt()
        LONG -> value.toLong()
        BIG_INTEGER -> BigDecimal(BigInteger(value))
        FLOAT -> value.toFloat().also { require(it.isFinite()) }
        DOUBLE -> value.toDouble().also { require(it.isFinite()) }
        DECIMAL -> BigDecimal(value)
        STRING -> value
        BYTES -> Base64.getDecoder().decode(value)
        DATE -> LocalDate.parse(value)
        TIME -> LocalTime.parse(value)
        TIMESTAMP -> LocalDateTime.parse(value)
        TIMESTAMP_TZ -> OffsetDateTime.parse(value)
        UUID -> java.util.UUID.fromString(value)
    }

    fun bind(statement: java.sql.PreparedStatement, index: Int, value: Any?, connection: Connection) {
        if (value == null) statement.setNull(index, jdbc)
        else if (connection.metaData.databaseProductName == "SQLite" &&
            this in setOf(DATE, TIME, TIMESTAMP, TIMESTAMP_TZ, UUID)
        ) statement.setString(index, value.toString())
        else statement.setObject(index, value)
    }

    fun read(row: ResultSet, index: Int): JsonPrimitive? = when (this) {
        BOOLEAN -> row.getBoolean(index).let { if (row.wasNull()) null else JsonPrimitive(it) }
        INT -> row.getInt(index).let { if (row.wasNull()) null else JsonPrimitive(it) }
        LONG -> row.getLong(index).let { if (row.wasNull()) null else JsonPrimitive(it) }
        BIG_INTEGER, DECIMAL -> row.getBigDecimal(index)?.let { JsonPrimitive(it) }
        FLOAT -> row.getFloat(index).let { if (row.wasNull()) null else JsonPrimitive(it) }
        DOUBLE -> row.getDouble(index).let { if (row.wasNull()) null else JsonPrimitive(it) }
        BYTES -> row.getBytes(index)?.let { JsonPrimitive(Base64.getEncoder().encodeToString(it)) }
        else -> row.getObject(index)?.toString()?.let { JsonPrimitive(it) }
    }
}

internal fun Route.generatedResource(store: SystemStore, spec: ResourceSpec) = route(spec.path) {
    get {
        val token = call.bearer()
        val window = call.window()
        val filters = spec.filters(call)
        call.respond(store.read(token, "${spec.authority}:read") { db, _ ->
            val where = filters.where()
            val count = db.query("SELECT COUNT(*) FROM ${spec.table}$where", filters) { it.getLong(1) }.single()
            window.result(spec.page(db, filters, window.size, window.offset), count)
        })
    }
    get("{id}") {
        val key = spec.pathId(call)
        call.respond(store.read(call.bearer(), "${spec.authority}:read") { db, _ ->
            spec.byId(db, key) ?: missing("记录")
        })
    }
    post {
        val raw = call.bearer()
        val values = if (spec.create.isEmpty()) emptyList() else spec.values(call.receive<JsonObject>(), updating = false)
        call.respond(HttpStatusCode.Created, store.write(raw, "${spec.authority}:create") { db, _, audit ->
            val id = resourceConstraint { spec.insert(db, values) }
            audit("${spec.authority}:create", checkedTarget(id))
            buildJsonObject { put("id", id) }
        })
    }
    if (spec.update.isNotEmpty()) put("{id}") {
        val raw = call.bearer()
        val key = spec.pathId(call)
        val values = spec.values(call.receive<JsonObject>(), updating = true)
        store.write(raw, "${spec.authority}:update") { db, _, audit ->
            val changed = resourceConstraint {
                db.execute(
                    "UPDATE ${spec.table} SET ${values.joinToString(", ") { "${it.first.sql} = ?" }} WHERE ${spec.key.sql} = ?",
                    values + (spec.key to key),
                )
            }
            if (changed == 0 && spec.byId(db, key) == null) missing("记录")
            audit("${spec.authority}:update", checkedTarget(key))
        }
        call.respond(HttpStatusCode.NoContent)
    }
    delete("{id}") {
        val key = spec.pathId(call)
        store.write(call.bearer(), "${spec.authority}:delete") { db, _, audit ->
            val changed = resourceConstraint { db.execute("DELETE FROM ${spec.table} WHERE ${spec.key.sql} = ?", listOf(spec.key to key)) }
            if (changed == 0) missing("记录")
            audit("${spec.authority}:delete", checkedTarget(key))
        }
        call.respond(HttpStatusCode.NoContent)
    }
    get("excel/export") {
        val raw = call.bearer()
        val filters = spec.filters(call)
        val rows = store.read(raw, "${spec.authority}:read") { db, _ -> spec.page(db, filters, 1001, 0) }
        if (rows.size > 1000) invalid("最多导出 1000 条记录，请缩小筛选范围")
        val columns = spec.excelExport
        val payload = withContext(Dispatchers.IO) {
            XlsxWorkbooks.write(columns.map { it.heading }, rows.map { row ->
                columns.map {
                    val result = row[it.property] as? JsonPrimitive
                    if (result == null || result == JsonNull) null
                    else if (it.kind == ResourceKind.BOOLEAN) result.content == "true"
                    else if (it.kind == ResourceKind.INT) result.content.toInt()
                    else result.content
                }
            })
        }
        call.workbook("记录.xlsx", payload)
    }
    if (spec.excelCreate.isNotEmpty()) {
        val columns = spec.excelCreate.map { XlsxColumn(it.heading, 32_767, it.kind !in setOf(ResourceKind.INT, ResourceKind.BOOLEAN)) }
        get("excel/template") {
            store.read(call.bearer(), "${spec.authority}:create") { _, _ -> Unit }
            val payload = withContext(Dispatchers.IO) { XlsxWorkbooks.template(columns) }
            call.workbook("导入模板.xlsx", payload)
        }
        post("excel/import") {
            val raw = call.bearer()
            store.read(raw, "${spec.authority}:create") { _, _ -> Unit }
            val rows = call.readWorkbook(columns, 1000)
            store.write(raw, "${spec.authority}:create") { db, _, audit ->
                for (row in rows) {
                    try {
                        val body = buildJsonObject {
                            spec.excelCreate.zip(row.cells).forEach { (column, value) ->
                                if (value.isNotEmpty()) put(column.property, JsonPrimitive(value))
                            }
                        }
                        val id = resourceConstraint { spec.insert(db, spec.values(body, updating = false)) }
                        audit("${spec.authority}:create", checkedTarget(id))
                    } catch (failure: SystemFailure) {
                        throw SystemFailure(failure.status, "第 ${row.number} 行：${failure.message}")
                    }
                }
            }
            call.respond(HttpStatusCode.Created, buildJsonObject { put("imported", JsonPrimitive(rows.size)) })
        }
    }
}

private typealias Fields = List<Pair<ResourceColumn, Any?>>

private fun ResourceSpec.filters(call: ApplicationCall): Fields = buildList {
    for ((parameter, values) in call.request.queryParameters.entries()) {
        if (!parameter.startsWith("filter.")) continue
        val property = parameter.removePrefix("filter.")
        val column = columns.firstOrNull { it.property == property && it.kind != ResourceKind.BYTES }
            ?: invalid("不支持筛选字段 $property")
        if (values.size != 1) invalid("筛选字段 $property 只允许一个值")
        add(column to column.convert(values.single()))
    }
}

private fun ResourceSpec.values(body: JsonObject, updating: Boolean): Fields {
    val allowed = if (updating) update else create
    if (body.keys.any { name -> allowed.none { it.property == name } }) invalid("请求包含不可写字段")
    return buildList {
        for (column in allowed) {
            val data = body[column.property]
            if (data == null || data == JsonNull) {
                if (updating) {
                    if (!column.nullable) invalid("${column.property} 为必填项")
                    add(column to null)
                } else {
                    if (required(column)) invalid("${column.property} 为必填项")
                    if (!column.defaulted) add(column to null)
                }
            } else {
                val primitive = data as? JsonPrimitive ?: invalid("${column.property} 格式无效")
                add(column to column.convert(primitive.content))
            }
        }
    }
}

private fun ResourceColumn.convert(value: String): Any = try { kind.parse(value) }
catch (_: IllegalArgumentException) { invalid("$property 格式无效") }
catch (_: java.time.DateTimeException) { invalid("$property 格式无效") }

private fun ResourceSpec.pathId(call: ApplicationCall): Any = key.convert(checkedTarget(call.id()))

private fun checkedTarget(value: Any): String {
    val id = if (value is JsonPrimitive) value.content else value.toString()
    if (id.isBlank() || id.length > 120 || id in setOf(".", "..") ||
        id.any { Character.isISOControl(it) || it in "/\\;%" }
    ) invalid("主键须为合法路径段")
    return id
}

private fun Fields.where(): String = if (isEmpty()) ""
else joinToString(" AND ", prefix = " WHERE ") { "${it.first.sql} = ?" }

private fun ResourceSpec.from(row: ResultSet): JsonObject = buildJsonObject {
    columns.forEachIndexed { index, column -> put(column.property, column.kind.read(row, index + 1) ?: JsonNull) }
}

private fun ResourceSpec.page(db: Connection, filters: Fields, size: Int, offset: Long): List<JsonObject> {
    val where = filters.where()
    return db.prepareStatement("SELECT $selected FROM $table$where ORDER BY ${key.sql} LIMIT ? OFFSET ?").use { query ->
        filters.forEachIndexed { i, (column, value) -> column.kind.bind(query, i + 1, value, db) }
        query.setInt(filters.size + 1, size)
        query.setLong(filters.size + 2, offset)
        query.executeQuery().use { rows -> buildList { while (rows.next()) add(from(rows)) } }
    }
}

private fun ResourceSpec.byId(db: Connection, id: Any): JsonObject? =
    db.query("SELECT $selected FROM $table WHERE ${key.sql} = ?", listOf(key to id), ::from).singleOrNull()

private fun ResourceSpec.insert(db: Connection, values: Fields): JsonPrimitive {
    val insert = when {
        values.isNotEmpty() -> {
            val names = values.joinToString(", ") { it.first.sql }
            val marks = values.joinToString(", ") { "?" }
            "INSERT INTO $table ($names) VALUES ($marks)"
        }
        mysql -> "INSERT INTO $table () VALUES ()"
        else -> "INSERT INTO $table DEFAULT VALUES"
    }
    if (!key.identity) {
        val id = values.single { it.first == key }.second ?: invalid("缺少主键")
        checkedTarget(id)
        check(db.execute(insert, values) == 1)
        return if (id is Number) JsonPrimitive(id) else JsonPrimitive(id.toString())
    }
    return db.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS).use { command ->
        values.forEachIndexed { i, (column, value) -> column.kind.bind(command, i + 1, value, db) }
        check(command.executeUpdate() == 1)
        command.generatedKeys.use { keys ->
            check(keys.next()) { "数据库未返回新主键" }
            checkNotNull(key.kind.read(keys, 1))
        }
    }
}

private fun Connection.execute(sql: String, fields: Fields): Int = prepareStatement(sql).use { command ->
    fields.forEachIndexed { i, (column, value) -> column.kind.bind(command, i + 1, value, this) }
    command.executeUpdate()
}

private fun <T> Connection.query(sql: String, fields: Fields, map: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { command ->
        fields.forEachIndexed { i, (column, value) -> column.kind.bind(command, i + 1, value, this) }
        command.executeQuery().use { rows -> buildList { while (rows.next()) add(map(rows)) } }
    }

private inline fun <T> resourceConstraint(action: () -> T): T = try { action() } catch (failure: SQLException) {
    val sqlite = (failure as? SQLiteException)?.resultCode
    when {
        failure.sqlState == "23505" || failure.errorCode == 1062 ||
            sqlite in setOf(SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY, SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE, SQLiteErrorCode.SQLITE_CONSTRAINT_ROWID) ->
            throw SystemFailure(409, "记录已存在")
        failure.sqlState?.startsWith("23") == true || sqlite?.code?.and(0xff) == SQLiteErrorCode.SQLITE_CONSTRAINT.code ->
            invalid("数据不符合数据库约束")
        else -> throw failure
    }
}

/** 在这里挂载生成的业务路由；保持权限声明和 SQL 迁移在相应模块内。 */
internal fun Route.businessRoutes(store: SystemStore) {
    // 例如：registerOrdersRoutes(store)
}
