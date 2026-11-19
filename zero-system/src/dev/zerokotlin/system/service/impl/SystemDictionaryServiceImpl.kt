package dev.zerokotlin.system.service.impl

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl
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
import dev.zerokotlin.system.security.SystemActor
import dev.zerokotlin.system.service.SystemDictionaryService
import dev.zerokotlin.system.service.SystemWrites
import dev.zerokotlin.system.support.EXCEL_EXPORT_ROWS
import dev.zerokotlin.system.support.ExcelImportRow
import dev.zerokotlin.system.support.atExcelRow
import dev.zerokotlin.system.support.checkedCode
import dev.zerokotlin.system.support.checkedDeleteIds
import dev.zerokotlin.system.support.checkedText
import dev.zerokotlin.system.support.conflict
import dev.zerokotlin.system.support.flag
import dev.zerokotlin.system.support.invalid
import dev.zerokotlin.system.support.missing
import dev.zerokotlin.system.support.newId
import dev.zerokotlin.system.support.query
import dev.zerokotlin.system.support.requiredFlag
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 将持久化字典记录转换为业务摘要。
 *
 * @receiver 待读取的字典记录。
 * @return 含编码、名称与布尔启用状态的字典摘要。
 */
private fun DictionaryRecord.summary() = DictionarySummary(id, code, name, enabled == 1)

/**
 * 将持久化字典项转换为接口条目。
 *
 * @receiver 待转换的字典项记录。
 * @return 含选项值、标签、排序值与布尔启用状态的条目。
 */
private fun DictionaryItemRecord.item() = DictionaryItem(id, itemValue, label, sortOrder, enabled == 1)

/**
 * 查询和维护字典及其选项。新增、修改及对应的操作审计在同一事务提交。
 *
 * @property dictionaries 按 ID 或业务编码查找和保存字典。
 * @property items 读写字典项，并检查同一字典下的值是否重复。
 * @property writes 串行化维护操作，校验字典写入权限并记录审计。
 */
@Service
class SystemDictionaryServiceImpl(
    private val items: DictionaryItemMapper,
    private val writes: SystemWrites,
) : ServiceImpl<DictionaryMapper, DictionaryRecord>(), SystemDictionaryService {
    private val dictionaries: DictionaryMapper get() = getBaseMapper()

    /**
     * 读取管理用字典目录。
     *
     * @return 按编码升序排列的所有字典摘要，含停用字典。
     */
    override fun summaries(): List<DictionarySummary> = listDictionaries()

    /** 读取可导出的字典目录，包含停用字典。 */
    override fun dictionariesForExcel(): List<DictionarySummary> =
        listDictionaries(EXCEL_EXPORT_ROWS + 1).also {
            if (it.size > EXCEL_EXPORT_ROWS) invalid("导出最多包含 $EXCEL_EXPORT_ROWS 个字典")
        }

    private fun listDictionaries(limit: Int? = null): List<DictionarySummary> =
        dictionaries.selectList(query<DictionaryRecord>().orderByAsc("code").last(limit != null, "LIMIT $limit"))
            .map(DictionaryRecord::summary)

    /**
     * 根据业务编码读取启用字典的可用选项。
     *
     * @param code 字典编码，格式无效时返回 400，字典不存在或已停用时返回 404。
     * @return 按排序值和 ID 升序排列的启用字典项。
     */
    @Transactional(readOnly = true)
    override fun values(code: String): List<DictionaryItem> {
        val dictionary = dictionaries.selectOne(query<DictionaryRecord>().eq("code", checkedCode(code)).eq("enabled", 1))
            ?: missing("字典")
        return listItems(dictionary.id, enabledOnly = true)
    }

    /**
     * 批量读取启用字典的可用选项，跳过停用或不存在的字典编码。
     *
     * @param codes 1～100 个编码，重复编码合并。
     * @return 以字典编码为键的选项列表，每组按排序值和 ID 升序排列。
     */
    @Transactional(readOnly = true)
    override fun valuesByCodes(codes: List<String?>): Map<String, List<DictionaryItem>> {
        if (codes.size !in 1..100) invalid("每次须查询 1 至 100 个字典编码")
        val uniqueCodes = codes.map { checkedCode(it ?: invalid("字典编码无效")) }.toSet()
        val matched = dictionaries.selectList(
            query<DictionaryRecord>().`in`("code", uniqueCodes).eq("enabled", 1),
        ).filter { it.code in uniqueCodes }.associateBy { it.code }
        if (matched.isEmpty()) return emptyMap()
        val grouped = items.selectList(
            query<DictionaryItemRecord>().`in`("dictionary_id", matched.values.map { it.id })
                .eq("enabled", 1).orderByAsc("sort_order", "id"),
        ).groupBy(DictionaryItemRecord::dictionaryId, DictionaryItemRecord::item)
        return uniqueCodes.filter { it in matched }.associateWith { grouped[matched.getValue(it).id].orEmpty() }
    }

    /**
     * 按字典 ID 查询管理用选项。
     *
     * @param dictionaryId 所属字典 ID，不存在时返回 404。
     * @return 按排序值和 ID 升序排列的全部字典项，含停用项。
     */
    override fun items(dictionaryId: String): List<DictionaryItem> = listItems(dictionary(dictionaryId).id, enabledOnly = false)

    /** 读取指定字典中可导出的字典项，包含停用项。 */
    @Transactional(readOnly = true)
    override fun itemsForExcel(dictionaryId: String): List<DictionaryItem> =
        listItems(dictionary(dictionaryId).id, enabledOnly = false, limit = EXCEL_EXPORT_ROWS + 1).also {
            if (it.size > EXCEL_EXPORT_ROWS) invalid("导出最多包含 $EXCEL_EXPORT_ROWS 个字典项")
        }

    /** 校验字典项操作指定的父字典，包含停用字典。 */
    override fun requireDictionary(dictionaryId: String) {
        dictionary(dictionaryId)
    }

    /**
     * 校验编码后新建字典。编码已被占用时返回 409。
     *
     * @param request 新字典的唯一编码和显示名称。
     * @return 新字典 ID。
     */
    @Transactional
    override fun create(request: CreateDictionaryRequest): CreatedResource {
        val actor = writes.authorize(DICTIONARY_WRITE)
        return insertDictionary(request, actor)
    }

    /** 在同一笔授权事务中新增全部字典。任意一行失败时回滚新增内容及审计。 */
    @Transactional
    override fun importExcel(rows: List<ExcelImportRow<CreateDictionaryRequest>>) {
        val actor = writes.authorize(DICTIONARY_WRITE)
        rows.forEach { row -> atExcelRow(row.number) { insertDictionary(row.request, actor) } }
    }

    private fun insertDictionary(request: CreateDictionaryRequest, actor: SystemActor): CreatedResource {
        val code = checkedCode(request.code)
        if (dictionaries.selectCount(query<DictionaryRecord>().eq("code", code)) != 0L) conflict("字典编码已存在")

        val id = newId()
        dictionaries.insert(DictionaryRecord(id, code, checkedText(request.name, "name")))
        writes.record(actor, "dictionary.create", id)
        return CreatedResource(id)
    }

    /**
     * 修改已有字典的名称和启用状态。
     *
     * @param id 待修改的字典 ID，不存在时返回 404。
     * @param request 字典的新名称及必填的启用状态。
     */
    @Transactional
    override fun update(id: String, request: UpdateDictionaryRequest) {
        val actor = writes.authorize(DICTIONARY_WRITE)
        val dictionary = dictionary(id)
        dictionary.name = checkedText(request.name, "name")
        dictionary.enabled = request.enabled.requiredFlag()
        dictionaries.updateById(dictionary)
        writes.record(actor, "dictionary.update", dictionary.id)
    }

    /**
     * 向已有字典添加选项。同一字典内的值重复时返回 409。
     *
     * @param dictionaryId 所属字典 ID，不存在时返回 404。
     * @param request 新字典项的值、标签、排序值和启用状态。
     * @return 新字典项 ID。
     */
    @Transactional
    override fun createItem(dictionaryId: String, request: DictionaryItemRequest): CreatedResource {
        val actor = writes.authorize(DICTIONARY_WRITE)
        val dictionary = dictionary(dictionaryId)
        return insertItem(dictionary.id, request, actor)
    }

    /** 在同一笔授权事务中向指定字典新增全部字典项。任意一行失败时回滚新增内容及审计。 */
    @Transactional
    override fun importItemsExcel(dictionaryId: String, rows: List<ExcelImportRow<DictionaryItemRequest>>) {
        val actor = writes.authorize(DICTIONARY_WRITE)
        val dictionary = dictionary(dictionaryId)
        rows.forEach { row -> atExcelRow(row.number) { insertItem(dictionary.id, row.request, actor) } }
    }

    private fun insertItem(dictionaryId: String, request: DictionaryItemRequest, actor: SystemActor): CreatedResource {
        val id = newId()
        items.insert(item(id, dictionaryId, request))
        writes.record(actor, "dictionary.item.create", id)
        return CreatedResource(id)
    }

    /**
     * 更新指定字典中的选项。目标字典或其下的指定选项不存在时返回 404。
     *
     * @param dictionaryId 所属字典 ID。
     * @param id 待修改的字典项 ID。
     * @param request 字典项的新值、标签、排序值和启用状态。
     */
    @Transactional
    override fun updateItem(dictionaryId: String, id: String, request: DictionaryItemRequest) {
        val actor = writes.authorize(DICTIONARY_WRITE)
        val dictionary = dictionary(dictionaryId)
        val existing = items.selectById(id)?.takeIf { it.dictionaryId == dictionary.id } ?: missing("字典项")
        items.updateById(item(existing.id, dictionary.id, request))
        writes.record(actor, "dictionary.item.update", existing.id)
    }

    /** 原子删除没有字典项引用的一组字典。 */
    @Transactional
    override fun delete(ids: List<String?>) {
        val actor = writes.authorize(DICTIONARY_WRITE, administratorOnly = true)
        val targets = checkedDeleteIds(ids)
        for (id in targets) {
            if (dictionary(id).id != id) missing("字典")
            if (items.selectCount(query<DictionaryItemRecord>().eq("dictionary_id", id)) != 0L) {
                conflict("请先删除字典下的全部字典项")
            }
        }
        for (id in targets) {
            if (dictionaries.deleteById(id) != 1) missing("字典")
            writes.record(actor, "dictionary.delete", id)
        }
    }

    /** 原子删除同一字典下的一组字典项，包含停用项。 */
    @Transactional
    override fun deleteItems(dictionaryId: String, ids: List<String?>) {
        val actor = writes.authorize(DICTIONARY_WRITE, administratorOnly = true)
        val dictionary = dictionary(dictionaryId)
        val targets = checkedDeleteIds(ids)
        val found = items.selectList(query<DictionaryItemRecord>().eq("dictionary_id", dictionary.id).`in`("id", targets))
        if (found.map { it.id }.toSet() != targets) missing("字典项")
        for (id in targets) {
            if (items.deleteById(id) != 1) missing("字典项")
            writes.record(actor, "dictionary.item.delete", id)
        }
    }

    /**
     * 按 ID 查找用于管理操作的字典，包括已停用的记录。
     *
     * @param id 要查询的字典 ID，不存在时抛出 HTTP 404 异常。
     * @return 匹配的字典记录。
     */
    private fun dictionary(id: String): DictionaryRecord = dictionaries.selectById(id) ?: missing("字典")

    /**
     * 读取一个字典的选项。
     *
     * @param dictionaryId 已查到的字典 ID。
     * @param enabledOnly 为 true 时仅匹配启用选项。
     * @param limit 查询行数上限，null 时读取全部匹配记录。
     * @return 按排序值和 ID 升序排列的字典项。
     */
    private fun listItems(dictionaryId: String, enabledOnly: Boolean, limit: Int? = null): List<DictionaryItem> =
        items.selectList(
            query<DictionaryItemRecord>().eq("dictionary_id", dictionaryId)
                .eq(enabledOnly, "enabled", 1).orderByAsc("sort_order", "id").last(limit != null, "LIMIT $limit"),
        ).map(DictionaryItemRecord::item)

    /**
     * 校验选项文本并检查同字典下其他选项的值。值重复时抛出 HTTP 409 异常。
     *
     * @param id 待写入的字典项 ID，用于排除更新场景中的当前记录。
     * @param dictionaryId 所属字典 ID，也是选项值的唯一性范围。
     * @param request 待写入的值、标签、排序值和启用状态。
     * @return 可供插入或更新的字典项记录。
     */
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
