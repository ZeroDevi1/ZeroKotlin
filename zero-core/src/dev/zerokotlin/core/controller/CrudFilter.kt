package dev.zerokotlin.core.controller

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper
import dev.zerokotlin.core.service.CrudValueConversion
import org.springframework.http.HttpStatus
import org.springframework.util.MultiValueMap
import org.springframework.util.ReflectionUtils
import org.springframework.web.server.ResponseStatusException

/** 允许用 `filter.<属性名>` 对此持久化属性作等值筛选。 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class CrudFilter

/** 从 MyBatis-Plus 元数据解析筛选列和默认排序，SQL 列名始终来自映射配置。 */
internal class CrudQuery<T : Any>(type: Class<T>) {
    val table = checkNotNull(TableInfoHelper.getTableInfo(type)) { "缺少 ${type.name} 的 MyBatis-Plus 映射" }
    private val keyColumn = checkNotNull(table.keyColumn) { "${type.name} 必须映射一列主键" }
    private val filters = buildMap {
        if (ReflectionUtils.findField(type, table.keyProperty)?.isAnnotationPresent(CrudFilter::class.java) == true) {
            put(table.keyProperty, keyColumn to table.keyType)
        }
        table.fieldList.filter { it.field.isAnnotationPresent(CrudFilter::class.java) }.forEach {
            put(it.property, it.column to it.propertyType)
        }
    }

    fun build(parameters: MultiValueMap<String, String>): QueryWrapper<T> {
        val query = QueryWrapper<T>().orderByAsc(keyColumn)
        for ((parameter, values) in parameters) {
            if (!parameter.startsWith("filter.")) continue
            val property = parameter.removePrefix("filter.")
            val (column, type) = filters[property] ?: invalid("不支持筛选字段 $property")
            if (values.size != 1) invalid("筛选字段 $property 只允许一个值")
            val value = try {
                CrudValueConversion.parse(values.single(), type)
            } catch (_: IllegalArgumentException) {
                invalid("筛选字段 $property 的值格式无效")
            }
            query.eq(column, value)
        }
        return query
    }

    private fun invalid(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
}
