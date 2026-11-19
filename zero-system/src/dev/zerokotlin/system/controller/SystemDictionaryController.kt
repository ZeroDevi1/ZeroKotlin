package dev.zerokotlin.system.controller

import dev.zerokotlin.system.dto.CreateDictionaryRequest
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.DictionaryItem
import dev.zerokotlin.system.dto.DictionaryItemRequest
import dev.zerokotlin.system.dto.DictionarySummary
import dev.zerokotlin.system.dto.UpdateDictionaryRequest
import dev.zerokotlin.system.security.DICTIONARY_WRITE
import dev.zerokotlin.system.service.SystemDictionaries
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 提供字典管理和按编码读取启用选项的接口。
 *
 * @property dictionaries 查询和维护字典及其选项。
 */
@RestController
@RequestMapping("/api/system/dictionaries")
class SystemDictionaryController(private val dictionaries: SystemDictionaries) {
    /**
     * 列出供管理界面使用的字典。
     *
     * @return 按编码升序排列的字典摘要，含启用和停用项。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('system:dictionary:read')")
    fun list(): List<DictionarySummary> = dictionaries.list()

    /**
     * 为已认证用户读取启用字典的可用选项。
     *
     * @param code 启用的字典编码。
     * @return 按排序值和 ID 升序排列的启用字典项。
     */
    @GetMapping("/by-code/{code}/items")
    fun values(@PathVariable code: String): List<DictionaryItem> = dictionaries.values(code)

    /**
     * 查询一个字典的管理明细。
     *
     * @param id 字典 ID。
     * @return 字典下的所有字典项，含停用项。
     */
    @GetMapping("/{id}/items")
    @PreAuthorize("hasAuthority('system:dictionary:read')")
    fun items(@PathVariable id: String): List<DictionaryItem> = dictionaries.items(id)

    /**
     * 登记一个新字典。
     *
     * @param request 唯一的字典编码和显示名称。
     * @return HTTP 201 响应体，包含新资源的 ID。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$DICTIONARY_WRITE')")
    fun create(@RequestBody request: CreateDictionaryRequest): CreatedResource = dictionaries.create(request)

    /**
     * 更新字典名称及启用状态，成功时返回 HTTP 204。
     *
     * @param id 待修改的字典 ID。
     * @param request 字典的新名称和启用状态。
     */
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$DICTIONARY_WRITE')")
    fun update(@PathVariable id: String, @RequestBody request: UpdateDictionaryRequest) = dictionaries.update(id, request)

    /**
     * 向指定字典添加一项。
     *
     * @param id 所属字典的 ID。
     * @param request 字典项的值、显示标签、排序值和启用状态。
     * @return HTTP 201 响应体，包含新资源的 ID。
     */
    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$DICTIONARY_WRITE')")
    fun createItem(@PathVariable id: String, @RequestBody request: DictionaryItemRequest): CreatedResource =
        dictionaries.createItem(id, request)

    /**
     * 修改指定字典内的字典项，成功时返回 HTTP 204。
     *
     * @param dictionaryId 所属字典的 ID。
     * @param id 待修改的字典项 ID。
     * @param request 新的字典项值、显示标签、排序值和启用状态。
     */
    @PutMapping("/{dictionaryId}/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$DICTIONARY_WRITE')")
    fun updateItem(
        @PathVariable dictionaryId: String,
        @PathVariable id: String,
        @RequestBody request: DictionaryItemRequest,
    ) = dictionaries.updateItem(dictionaryId, id, request)
}
