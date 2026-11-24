package dev.zerokotlin.ktor

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement

/** 兼容早期生成文件使用的 ResourceSpec，继续沿用其字段校验和 HTTP 契约。 */
internal class JdbcResourceMapper(private val spec: ResourceSpec) : ResourceMapper {
    override fun list(db: Connection, filters: ResourceFields, window: Window): Page<JsonObject> {
        val count = db.query("SELECT COUNT(*) FROM ${spec.table}${filters.where()}", filters) { it.getLong(1) }.single()
        return window.result(spec.page(db, filters, window.size, window.offset), count)
    }

    override fun export(db: Connection, filters: ResourceFields): List<JsonObject> = spec.page(db, filters, 1001, 0)

    override fun find(db: Connection, id: Any, scope: ResourceFields): JsonObject? {
        val filters = listOf(spec.key to id) + scope
        return db.query("SELECT ${spec.selected} FROM ${spec.table}${filters.where()}", filters) { spec.from(it) }.singleOrNull()
    }

    override fun create(db: Connection, values: ResourceFields): JsonPrimitive = spec.insert(db, values)

    override fun update(db: Connection, id: Any, values: ResourceFields, scope: ResourceFields): Int {
        val assignment = values.joinToString(", ") { "${it.first.sql} = ?" }
        val filters = listOf(spec.key to id) + scope
        return db.execute("UPDATE ${spec.table} SET $assignment${filters.where()}", values + filters)
    }

    override fun delete(db: Connection, id: Any, scope: ResourceFields): Int {
        val filters = listOf(spec.key to id) + scope
        return db.execute("DELETE FROM ${spec.table}${filters.where()}", filters)
    }
}

private fun ResourceFields.where(): String = if (isEmpty()) ""
else joinToString(" AND ", prefix = " WHERE ") { "${it.first.sql} = ?" }

private fun ResourceSpec.from(row: ResultSet): JsonObject = buildJsonObject {
    columns.forEachIndexed { index, column -> put(column.property, column.kind.read(row, index + 1) ?: JsonNull) }
}

private fun ResourceSpec.page(db: Connection, filters: ResourceFields, size: Int, offset: Long): List<JsonObject> {
    val where = filters.where()
    return db.prepareStatement("SELECT $selected FROM $table$where ORDER BY ${key.sql} LIMIT ? OFFSET ?").use { query ->
        filters.forEachIndexed { i, (column, value) -> column.kind.bind(query, i + 1, value, db) }
        query.setInt(filters.size + 1, size)
        query.setLong(filters.size + 2, offset)
        query.executeQuery().use { rows -> buildList { while (rows.next()) add(from(rows)) } }
    }
}

private fun ResourceSpec.insert(db: Connection, values: ResourceFields): JsonPrimitive {
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

private fun Connection.execute(sql: String, fields: ResourceFields): Int = prepareStatement(sql).use { command ->
    fields.forEachIndexed { i, (column, value) -> column.kind.bind(command, i + 1, value, this) }
    command.executeUpdate()
}

private fun <T> Connection.query(sql: String, fields: ResourceFields, map: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { command ->
        fields.forEachIndexed { i, (column, value) -> column.kind.bind(command, i + 1, value, this) }
        command.executeQuery().use { rows -> buildList { while (rows.next()) add(map(rows)) } }
    }
