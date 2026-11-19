package dev.zerokotlin.system.service

import com.baomidou.mybatisplus.spring.service.IService
import dev.zerokotlin.system.dto.CreateDictionaryRequest
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.DictionaryItem
import dev.zerokotlin.system.dto.DictionaryItemRequest
import dev.zerokotlin.system.dto.DictionarySummary
import dev.zerokotlin.system.dto.UpdateDictionaryRequest
import dev.zerokotlin.system.entity.DictionaryRecord
import dev.zerokotlin.system.support.ExcelImportRow

/** 提供字典与字典项相关的业务操作。 */
interface SystemDictionaryService : IService<DictionaryRecord> {
    fun summaries(): List<DictionarySummary>
    fun dictionariesForExcel(): List<DictionarySummary>
    fun values(code: String): List<DictionaryItem>
    fun valuesByCodes(codes: List<String?>): Map<String, List<DictionaryItem>>
    fun items(dictionaryId: String): List<DictionaryItem>
    fun itemsForExcel(dictionaryId: String): List<DictionaryItem>
    fun requireDictionary(dictionaryId: String)
    fun create(request: CreateDictionaryRequest): CreatedResource
    fun importExcel(rows: List<ExcelImportRow<CreateDictionaryRequest>>)
    fun update(id: String, request: UpdateDictionaryRequest)
    fun createItem(dictionaryId: String, request: DictionaryItemRequest): CreatedResource
    fun importItemsExcel(dictionaryId: String, rows: List<ExcelImportRow<DictionaryItemRequest>>)
    fun updateItem(dictionaryId: String, id: String, request: DictionaryItemRequest)
    fun delete(ids: List<String?>)
    fun deleteItems(dictionaryId: String, ids: List<String?>)
}
