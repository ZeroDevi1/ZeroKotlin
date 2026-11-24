package dev.zerokotlin.ktor.system.dictionary

import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.SystemStore
import dev.zerokotlin.ktor.checkedDeleteIds
import dev.zerokotlin.ktor.invalid
import dev.zerokotlin.ktor.missing
import dev.zerokotlin.ktor.newId

/** 删除先核验归属和关联，再在同一事务更新记录与审计。 */
internal class DictionaryServiceImpl(private val store: SystemStore) : DictionaryService {
    override suspend fun list(token: String): List<Dictionary> =
        store.read(token, dictionaryRead) { db, _ -> store.withDictionaries(db) { list() } }

    override suspend fun byCode(token: String, code: String?): List<DictionaryItem> {
        val key = checkedCode(code)
        return store.read(token, null) { db, _ ->
            store.withDictionaries(db) { items(enabledId(key) ?: missing("字典"), enabledOnly = true) }
        }
    }

    override suspend fun byCodes(token: String, codes: List<String?>): Map<String, List<DictionaryItem>> {
        if (codes.size !in 1..100) invalid("每次须查询 1 至 100 个字典编码")
        val keys = codes.map(::checkedCode).distinct()
        return store.read(token, null) { db, _ ->
            store.withDictionaries(db) {
                keys.mapNotNull { code -> enabledId(code)?.let { code to items(it, enabledOnly = true) } }.toMap()
            }
        }
    }

    override suspend fun create(token: String, request: NewDictionary): Created {
        val id = newId()
        store.write(token, dictionaryWrite) { db, _, audit ->
            store.withDictionaries(db) { insert(id, request.code, request.name) }
            audit("dictionary.create", id)
        }
        return Created(id)
    }

    override suspend fun update(token: String, id: String, request: EditDictionary) {
        store.write(token, dictionaryWrite) { db, _, audit ->
            store.withDictionaries(db) { update(id, request) }
            audit("dictionary.update", id)
        }
    }

    override suspend fun delete(token: String, ids: List<String?>) {
        store.write(token, dictionaryWrite, administratorOnly = true) { db, _, audit ->
            val targets = checkedDeleteIds(ids)
            store.withDictionaries(db) {
                targets.forEach { id ->
                    requireDictionary(id)
                    if (hasItems(id)) throw SystemFailure(409, "请先删除字典下的全部字典项")
                }
                targets.forEach { id ->
                    delete(id)
                    audit("dictionary.delete", id)
                }
            }
        }
    }

    override suspend fun items(token: String, dictionary: String): List<DictionaryItem> =
        store.read(token, dictionaryRead) { db, _ ->
            store.withDictionaries(db) {
                requireDictionary(dictionary)
                items(dictionary, enabledOnly = false)
            }
        }

    override suspend fun createItem(token: String, dictionary: String, request: EditItem): Created {
        val id = newId()
        writeItem(token, dictionary, id, request, create = true)
        return Created(id)
    }

    override suspend fun updateItem(token: String, dictionary: String, id: String, request: EditItem) {
        writeItem(token, dictionary, id, request, create = false)
    }

    private suspend fun writeItem(token: String, dictionary: String, id: String, request: EditItem, create: Boolean) {
        store.write(token, dictionaryWrite) { db, _, audit ->
            store.withDictionaries(db) { saveItem(dictionary, id, request, create) }
            audit(if (create) "dictionary.item.create" else "dictionary.item.update", id)
        }
    }

    override suspend fun deleteItems(token: String, dictionary: String, ids: List<String?>) {
        store.write(token, dictionaryWrite, administratorOnly = true) { db, _, audit ->
            store.withDictionaries(db) {
                requireDictionary(dictionary)
                checkedDeleteIds(ids).forEach { id ->
                    deleteItem(dictionary, id)
                    audit("dictionary.item.delete", id)
                }
            }
        }
    }
}
