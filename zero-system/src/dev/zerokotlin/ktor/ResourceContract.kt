package dev.zerokotlin.ktor

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.math.BigInteger
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Types
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.Base64

/** SQL 标识符由生成器提供；请求参数只作为绑定值，不拼接进 SQL。 */
internal data class ResourceSpec(
    val path: String = "",
    val table: String,
    val mysql: Boolean,
    val authority: String,
    val columns: List<ResourceColumn>,
    val sqliteText: Boolean = false,
    val managedFields: Set<String> = emptySet(),
) {
    init {
        require(managedFields.all { name -> columns.any { it.property == name && !it.computed && !it.identity } }) {
            "服务端托管列须可写且与实体属性匹配"
        }
    }

    val key = columns.first()
    val create = columns.filterNot { it.identity || it.computed || it.property in managedFields }
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

internal typealias ResourceFields = List<Pair<ResourceColumn, Any?>>

internal fun ResourceSpec.filters(query: Map<String, List<String>>): ResourceFields = buildList {
    for ((parameter, values) in query.entries) {
        if (!parameter.startsWith("filter.")) continue
        val property = parameter.removePrefix("filter.")
        val column = columns.firstOrNull { it.property == property && it.kind != ResourceKind.BYTES }
            ?: invalid("不支持筛选字段 $property")
        if (values.size != 1) invalid("筛选字段 $property 只允许一个值")
        add(column to column.convert(values.single()))
    }
}

internal fun ResourceSpec.values(body: JsonObject, updating: Boolean): ResourceFields {
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

internal fun ResourceSpec.pathId(id: String): Any = key.convert(checkedTarget(id))

internal fun checkedTarget(value: Any): String {
    val id = if (value is JsonPrimitive) value.content else value.toString()
    if (id.isBlank() || id.length > 120 || id in setOf(".", "..") ||
        id.any { Character.isISOControl(it) || it in "/\\;%" }
    ) invalid("主键须为合法路径段")
    return id
}
