package dev.zerokotlin.ktor.system.dictionary

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.ktor.SystemStore
import dev.zerokotlin.ktor.checkedName
import dev.zerokotlin.ktor.flag
import dev.zerokotlin.ktor.missing
import java.sql.Connection

@TableName("sys_dictionary")
internal class DictionaryRecord {
    @field:TableId(type = IdType.INPUT)
    var id: String? = null
    var code: String? = null
    var name: String? = null
    var enabled: Int? = null
}

@TableName("sys_dictionary_item")
internal class DictionaryItemRecord {
    @field:TableId(type = IdType.INPUT)
    var id: String? = null
    @field:TableField("dictionary_id")
    var dictionaryId: String? = null
    @field:TableField("item_value")
    var value: String? = null
    var label: String? = null
    @field:TableField("sort_order")
    var sortOrder: Int? = null
    var enabled: Int? = null
}

internal interface DictionaryMapper : BaseMapper<DictionaryRecord>
internal interface DictionaryItemMapper : BaseMapper<DictionaryItemRecord>

/** 管理 API 与 Excel 共用 Mapper 和列校验，Mapper 只在当前事务内使用。 */
internal fun <T> SystemStore.withDictionaries(db: Connection, action: DictionaryData.() -> T): T =
    mappers.withSession(db) { session ->
        DictionaryData(
            session.getMapper(DictionaryMapper::class.java),
            session.getMapper(DictionaryItemMapper::class.java),
        ).action()
    }

internal class DictionaryData(
    private val dictionaries: DictionaryMapper,
    private val items: DictionaryItemMapper,
) {
    fun list(limit: Int? = null): List<Dictionary> {
        val query = QueryWrapper<DictionaryRecord>().orderByAsc("code")
        val records = if (limit == null) dictionaries.selectList(query)
        else dictionaries.selectPage(Page<DictionaryRecord>(1, limit.toLong(), false), query).records
        return records.map { Dictionary(checkNotNull(it.id), checkNotNull(it.code), checkNotNull(it.name), it.enabled == 1) }
    }

    fun enabledId(code: String): String? =
        dictionaries.selectOne(QueryWrapper<DictionaryRecord>().eq("code", code).eq("enabled", 1))?.id

    fun requireDictionary(id: String) {
        if (dictionaries.selectById(id) == null) missing("字典")
    }

    fun hasItems(dictionary: String): Boolean =
        items.selectCount(QueryWrapper<DictionaryItemRecord>().eq("dictionary_id", dictionary)) > 0L

    fun items(dictionary: String, enabledOnly: Boolean, limit: Int? = null): List<DictionaryItem> {
        val query = QueryWrapper<DictionaryItemRecord>().eq("dictionary_id", dictionary)
            .eq(enabledOnly, "enabled", 1).orderByAsc("sort_order", "id")
        val records = if (limit == null) items.selectList(query)
        else items.selectPage(Page<DictionaryItemRecord>(1, limit.toLong(), false), query).records
        return records.map {
            DictionaryItem(checkNotNull(it.id), checkNotNull(it.value), checkNotNull(it.label), checkNotNull(it.sortOrder), it.enabled == 1)
        }
    }

    fun insert(id: String, rawCode: String, name: String) {
        val code = checkedCode(rawCode)
        if (dictionaries.selectCount(QueryWrapper<DictionaryRecord>().eq("code", code)) > 0L) {
            throw SystemFailure(409, "字典编码已存在")
        }
        check(dictionaries.insert(DictionaryRecord().also {
            it.id = id
            it.code = code
            it.name = checkedName(name)
            it.enabled = 1
        }) == 1)
    }

    fun update(id: String, request: EditDictionary) {
        val changed = dictionaries.updateById(DictionaryRecord().also {
            it.id = id
            it.name = checkedName(request.name)
            it.enabled = request.enabled.flag()
        })
        if (changed == 0) missing("字典")
    }

    fun delete(id: String) {
        dictionaries.deleteById(id)
    }

    /** 整个更新条件包含父字典 ID，避免用其他字典项的 ID 越过归属条件。 */
    fun saveItem(dictionary: String, id: String, request: EditItem, create: Boolean) {
        requireDictionary(dictionary)
        val value = checkedName(request.value)
        val label = checkedName(request.label)
        val duplicate = QueryWrapper<DictionaryItemRecord>()
            .eq("dictionary_id", dictionary).eq("item_value", value).ne("id", id)
        if (items.selectCount(duplicate) > 0L) throw SystemFailure(409, "字典项值已存在")
        if (create) {
            check(items.insert(DictionaryItemRecord().also {
                it.id = id
                it.dictionaryId = dictionary
                it.value = value
                it.label = label
                it.sortOrder = request.sortOrder
                it.enabled = request.enabled.flag()
            }) == 1)
        } else {
            val changes = UpdateWrapper<DictionaryItemRecord>().eq("id", id).eq("dictionary_id", dictionary)
                .set("item_value", value).set("label", label).set("sort_order", request.sortOrder)
                .set("enabled", request.enabled.flag())
            if (items.update(null, changes) == 0) missing("字典项")
        }
    }

    fun deleteItem(dictionary: String, id: String) {
        val matches = QueryWrapper<DictionaryItemRecord>().eq("id", id).eq("dictionary_id", dictionary)
        if (items.delete(matches) != 1) missing("字典项")
    }
}
