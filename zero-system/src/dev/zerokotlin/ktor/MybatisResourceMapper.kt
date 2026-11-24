package dev.zerokotlin.ktor

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.baomidou.mybatisplus.extension.plugins.pagination.Page as MybatisPage
import dev.zerokotlin.core.database.MapperSessions
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.sql.Connection
import java.util.Base64
import kotlin.reflect.KMutableProperty1

/** Entity 属性与接口字段的绑定由生成器声明。列名始终在构建时固定。 */
internal class ResourceProperty<E>(
    val name: String,
    val get: (E) -> Any?,
    val set: (E, Any?) -> Unit,
)

internal fun <E, V> resourceProperty(property: KMutableProperty1<E, V?>): ResourceProperty<E> =
    ResourceProperty(property.name, property::get) { target, value ->
        // ResourceSpec 在进入 Mapper 前已按列的 Kotlin 类型转换。
        @Suppress("UNCHECKED_CAST")
        property.set(target, value as V?)
    }

/** 标准单表持久化使用 BaseMapper。所有操作使用同一个业务事务的连接。 */
internal class MybatisResourceMapper<E : Any, M : BaseMapper<E>>(
    private val sessions: MapperSessions,
    private val spec: ResourceSpec,
    private val mapperType: Class<M>,
    private val newRecord: () -> E,
    properties: List<ResourceProperty<E>>,
    private val insertDefault: ((M, E) -> Int)? = null,
) : ResourceMapper {
    private val properties = properties.associateBy(ResourceProperty<E>::name)

    override fun list(db: Connection, filters: ResourceFields, window: Window): Page<JsonObject> =
        use(db) { mapper ->
            val page = mapper.selectPage(MybatisPage<E>(window.page.toLong(), window.size.toLong()), query(filters).orderByAsc(spec.key.sql))
            window.result(page.records.map(::json), page.total)
        }

    override fun export(db: Connection, filters: ResourceFields): List<JsonObject> =
        use(db) { mapper ->
            mapper.selectPage(MybatisPage<E>(1, 1001, false), query(filters).orderByAsc(spec.key.sql)).records.map(::json)
        }

    override fun find(db: Connection, id: Any, scope: ResourceFields): JsonObject? =
        use(db) { mapper -> mapper.selectOne(query(keyAndScope(id, scope)))?.let(::json) }

    override fun create(db: Connection, values: ResourceFields): JsonPrimitive =
        use(db) { mapper ->
            val record = newRecord()
            values.forEach { (column, value) -> properties.getValue(column.property).set(record, column.databaseValue(value)) }
            if (!spec.key.identity) checkedTarget(checkNotNull(properties.getValue(spec.key.property).get(record)))
            // 自增表的所有列都由数据库提供时，使用该方言的空列 INSERT。
            val changed = if (values.isEmpty() && insertDefault != null) insertDefault.invoke(mapper, record) else mapper.insert(record)
            check(changed == 1) { "新增记录未返回一行" }
            checkNotNull(jsonValue(properties.getValue(spec.key.property).get(record))) { "数据库未返回新主键" }
        }

    override fun update(db: Connection, id: Any, values: ResourceFields, scope: ResourceFields): Int =
        use(db) { mapper ->
            val changes = UpdateWrapper<E>()
            values.forEach { (column, value) -> changes.set(column.sql, column.databaseValue(value)) }
            keyAndScope(id, scope).forEach { (column, value) ->
                if (value == null) changes.isNull(column.sql) else changes.eq(column.sql, column.databaseValue(value))
            }
            mapper.update(null, changes)
        }

    override fun delete(db: Connection, id: Any, scope: ResourceFields): Int =
        use(db) { mapper -> mapper.delete(query(keyAndScope(id, scope))) }

    private fun keyAndScope(id: Any, scope: ResourceFields) = listOf(spec.key to id) + scope

    private fun query(filters: ResourceFields) = QueryWrapper<E>().apply {
        filters.forEach { (column, value) ->
            if (value == null) isNull(column.sql) else eq(column.sql, column.databaseValue(value))
        }
    }

    // 保留 SQLite 的日期/时间与 UUID 文本格式，避免驱动把历史值转成其他表示。
    private fun ResourceColumn.databaseValue(value: Any?): Any? =
        if (spec.sqliteText && kind in sqliteTextKinds) value?.toString() else value

    private fun json(record: E) = buildJsonObject {
        spec.columns.forEach { column ->
            put(column.property, jsonValue(properties.getValue(column.property).get(record)) ?: JsonNull)
        }
    }

    private fun <T> use(db: Connection, action: (M) -> T): T =
        sessions.withSession(db) { action(it.getMapper(mapperType)) }
}

private val sqliteTextKinds = setOf(
    ResourceKind.DATE, ResourceKind.TIME, ResourceKind.TIMESTAMP, ResourceKind.TIMESTAMP_TZ, ResourceKind.UUID,
)

private fun jsonValue(value: Any?): JsonPrimitive? = when (value) {
    null -> null
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is ByteArray -> JsonPrimitive(Base64.getEncoder().encodeToString(value))
    else -> JsonPrimitive(value.toString())
}
