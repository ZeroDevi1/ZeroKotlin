package dev.zerokotlin.system.service

import dev.zerokotlin.system.dto.CreateDictionaryRequest
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.DictionaryItem
import dev.zerokotlin.system.dto.DictionaryItemRequest
import dev.zerokotlin.system.dto.DictionarySummary
import dev.zerokotlin.system.dto.UpdateDictionaryRequest
import dev.zerokotlin.system.entity.DictionaryItemRecord
import dev.zerokotlin.system.entity.DictionaryRecord
import dev.zerokotlin.system.mapper.DictionaryItemMapper
import dev.zerokotlin.system.mapper.DictionaryMapper
import dev.zerokotlin.system.security.DICTIONARY_WRITE
import dev.zerokotlin.system.support.checkedCode
import dev.zerokotlin.system.support.checkedText
import dev.zerokotlin.system.support.conflict
import dev.zerokotlin.system.support.flag
import dev.zerokotlin.system.support.missing
import dev.zerokotlin.system.support.newId
import dev.zerokotlin.system.support.query
import dev.zerokotlin.system.support.requiredFlag
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private fun DictionaryRecord.summary() = DictionarySummary(id, code, name, enabled == 1)

private fun DictionaryItemRecord.item() = DictionaryItem(id, itemValue, label, sortOrder, enabled == 1)

@Service
class SystemDictionaries(
    private val dictionaries: DictionaryMapper,
    private val items: DictionaryItemMapper,
    private val writes: SystemWrites,
) {
    fun list(): List<DictionarySummary> =
        dictionaries.selectList(query<DictionaryRecord>().orderByAsc("code")).map(DictionaryRecord::summary)

    @Transactional(readOnly = true)
    fun values(code: String): List<DictionaryItem> {
        val dictionary = dictionaries.selectOne(query<DictionaryRecord>().eq("code", checkedCode(code)).eq("enabled", 1))
            ?: missing("字典")
        return listItems(dictionary.id, enabledOnly = true)
    }

    fun items(dictionaryId: String): List<DictionaryItem> = listItems(dictionary(dictionaryId).id, enabledOnly = false)

    @Transactional
    fun create(request: CreateDictionaryRequest): CreatedResource {
        val actor = writes.authorize(DICTIONARY_WRITE)
        val code = checkedCode(request.code)
        if (dictionaries.selectCount(query<DictionaryRecord>().eq("code", code)) != 0L) conflict("字典编码已存在")

        val id = newId()
        dictionaries.insert(DictionaryRecord(id, code, checkedText(request.name, "name")))
        writes.record(actor, "dictionary.create", id)
        return CreatedResource(id)
    }

    @Transactional
    fun update(id: String, request: UpdateDictionaryRequest) {
        val actor = writes.authorize(DICTIONARY_WRITE)
        val dictionary = dictionary(id)
        dictionary.name = checkedText(request.name, "name")
        dictionary.enabled = request.enabled.requiredFlag()
        dictionaries.updateById(dictionary)
        writes.record(actor, "dictionary.update", dictionary.id)
    }

    @Transactional
    fun createItem(dictionaryId: String, request: DictionaryItemRequest): CreatedResource {
        val actor = writes.authorize(DICTIONARY_WRITE)
        val dictionary = dictionary(dictionaryId)
        val id = newId()
        items.insert(item(id, dictionary.id, request))
        writes.record(actor, "dictionary.item.create", id)
        return CreatedResource(id)
    }

    @Transactional
    fun updateItem(dictionaryId: String, id: String, request: DictionaryItemRequest) {
        val actor = writes.authorize(DICTIONARY_WRITE)
        val dictionary = dictionary(dictionaryId)
        val existing = items.selectById(id)?.takeIf { it.dictionaryId == dictionary.id } ?: missing("字典项")
        items.updateById(item(existing.id, dictionary.id, request))
        writes.record(actor, "dictionary.item.update", existing.id)
    }

    private fun dictionary(id: String): DictionaryRecord = dictionaries.selectById(id) ?: missing("字典")

    private fun listItems(dictionaryId: String, enabledOnly: Boolean): List<DictionaryItem> =
        items.selectList(
            query<DictionaryItemRecord>().eq("dictionary_id", dictionaryId)
                .eq(enabledOnly, "enabled", 1).orderByAsc("sort_order", "id"),
        ).map(DictionaryItemRecord::item)

    private fun item(id: String, dictionaryId: String, request: DictionaryItemRequest): DictionaryItemRecord {
        val value = checkedText(request.value, "value")
        if (items.selectCount(
                query<DictionaryItemRecord>().eq("dictionary_id", dictionaryId).eq("item_value", value).ne("id", id),
            ) != 0L) {
            conflict("字典项值已存在")
        }
        return DictionaryItemRecord(
            id, dictionaryId, value, checkedText(request.label, "label"), request.sortOrder, request.enabled.flag(),
        )
    }
}
