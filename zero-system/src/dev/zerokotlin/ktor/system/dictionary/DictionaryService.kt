package dev.zerokotlin.ktor.system.dictionary

import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.invalid
import dev.zerokotlin.ktor.permissionPattern
import kotlinx.serialization.Serializable

internal const val dictionaryRead = "system:dictionary:read"
internal const val dictionaryWrite = "system:dictionary:write"

@Serializable internal data class Dictionary(val id: String, val code: String, val name: String, val enabled: Boolean)
@Serializable internal data class DictionaryItem(val id: String, val value: String, val label: String, val sortOrder: Int, val enabled: Boolean)
@Serializable internal data class NewDictionary(val code: String, val name: String)
@Serializable internal data class EditDictionary(val name: String, val enabled: Boolean)
@Serializable internal data class DictionaryCodes(val codes: List<String?>)
@Serializable internal data class EditItem(val value: String, val label: String, val sortOrder: Int = 0, val enabled: Boolean = true)

/** 字典管理和业务选项读取。选项接口过滤停用的字典与字典项。 */
internal interface DictionaryService {
    suspend fun list(token: String): List<Dictionary>
    suspend fun byCode(token: String, code: String?): List<DictionaryItem>
    suspend fun byCodes(token: String, codes: List<String?>): Map<String, List<DictionaryItem>>
    suspend fun create(token: String, request: NewDictionary): Created
    suspend fun update(token: String, id: String, request: EditDictionary)
    suspend fun delete(token: String, ids: List<String?>)
    suspend fun items(token: String, dictionary: String): List<DictionaryItem>
    suspend fun createItem(token: String, dictionary: String, request: EditItem): Created
    suspend fun updateItem(token: String, dictionary: String, id: String, request: EditItem)
    suspend fun deleteItems(token: String, dictionary: String, ids: List<String?>)
}

internal fun checkedCode(code: String?) = code?.takeIf(permissionPattern::matches) ?: invalid("编码无效")
