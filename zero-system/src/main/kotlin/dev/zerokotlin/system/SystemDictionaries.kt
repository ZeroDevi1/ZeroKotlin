package dev.zerokotlin.system

import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

const val DICTIONARY_WRITE = "system:dictionary:write"

data class CreateDictionaryRequest(val code: String, val name: String)
data class UpdateDictionaryRequest(val name: String, val enabled: Boolean?)
data class DictionaryItemRequest(val value: String, val label: String, val sortOrder: Int = 0, val enabled: Boolean = true)

data class DictionarySummary(val id: String, val code: String, val name: String, val enabled: Boolean)
data class DictionaryItem(val id: String, val value: String, val label: String, val sortOrder: Int, val enabled: Boolean)

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

@RestController
@RequestMapping("/api/system/dictionaries")
class SystemDictionaryController(private val dictionaries: SystemDictionaries) {
    @GetMapping
    @PreAuthorize("hasAuthority('system:dictionary:read')")
    fun list(): List<DictionarySummary> = dictionaries.list()

    @GetMapping("/by-code/{code}/items")
    fun values(@PathVariable code: String): List<DictionaryItem> = dictionaries.values(code)

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAuthority('system:dictionary:read')")
    fun items(@PathVariable id: String): List<DictionaryItem> = dictionaries.items(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$DICTIONARY_WRITE')")
    fun create(@RequestBody request: CreateDictionaryRequest): CreatedResource = dictionaries.create(request)

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$DICTIONARY_WRITE')")
    fun update(@PathVariable id: String, @RequestBody request: UpdateDictionaryRequest) = dictionaries.update(id, request)

    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$DICTIONARY_WRITE')")
    fun createItem(@PathVariable id: String, @RequestBody request: DictionaryItemRequest): CreatedResource =
        dictionaries.createItem(id, request)

    @PutMapping("/{dictionaryId}/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$DICTIONARY_WRITE')")
    fun updateItem(
        @PathVariable dictionaryId: String,
        @PathVariable id: String,
        @RequestBody request: DictionaryItemRequest,
    ) = dictionaries.updateItem(dictionaryId, id, request)
}
