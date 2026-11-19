package dev.zerokotlin.system.controller

import dev.zerokotlin.system.dto.CreateDictionaryRequest
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.DeleteResourcesRequest
import dev.zerokotlin.system.dto.DictionaryCodesRequest
import dev.zerokotlin.system.dto.DictionaryItem
import dev.zerokotlin.system.dto.DictionaryItemRequest
import dev.zerokotlin.system.dto.DictionarySummary
import dev.zerokotlin.system.dto.UpdateDictionaryRequest
import dev.zerokotlin.system.security.DICTIONARY_WRITE
import dev.zerokotlin.system.service.SystemDictionaryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
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
@Tag(name = "字典管理")
class SystemDictionaryController(private val dictionaries: SystemDictionaryService) {
    /**
     * 列出供管理界面使用的字典。
     *
     * @return 按编码升序排列的字典摘要，含启用和停用项。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('system:dictionary:read')")
    @Operation(
        summary = "查询字典列表",
        description = "需要 `system:dictionary:read` 权限。返回全部字典，包含已停用的字典，按字典编码升序排列。",
        responses = [ApiResponse(responseCode = "200", description = "字典列表", useReturnTypeSchema = true)],
    )
    fun list(): List<DictionarySummary> = dictionaries.summaries()

    /**
     * 为已认证用户读取启用字典的可用选项。
     *
     * @param code 启用的字典编码。
     * @return 按排序值和 ID 升序排列的启用字典项。
     */
    @GetMapping("/by-code/{code}/items")
    @Operation(
        summary = "按字典编码查询启用的字典项",
        description = "登录后可查询启用字典中的启用项，按排序值和 ID 升序排列。",
        parameters = [Parameter(name = "code", `in` = ParameterIn.PATH, description = "字典编码")],
        responses = [
            ApiResponse(responseCode = "200", description = "已启用的字典项列表", useReturnTypeSchema = true),
            ApiResponse(responseCode = "400", description = "字典编码格式无效", content = [Content()]),
            ApiResponse(responseCode = "404", description = "字典不存在或已停用", content = [Content()]),
        ],
    )
    fun values(@PathVariable code: String): List<DictionaryItem> = dictionaries.values(code)

    /** 批量查询多个启用字典中的启用选项。 */
    @PostMapping("/by-codes/items")
    @Operation(
        summary = "按多个字典编码查询启用的字典项",
        description = "登录后可批量查询。结果按编码分组，缺失或停用的字典省略，启用但无可用字典项时返回空数组。",
        responses = [
            ApiResponse(responseCode = "200", description = "按字典编码分组的选项", useReturnTypeSchema = true),
            ApiResponse(responseCode = "400", description = "编码数量或格式无效", content = [Content()]),
        ],
    )
    fun valuesByCodes(@RequestBody request: DictionaryCodesRequest): Map<String, List<DictionaryItem>> =
        dictionaries.valuesByCodes(request.codes)

    /**
     * 查询一个字典的管理明细。
     *
     * @param id 字典 ID。
     * @return 字典下的所有字典项，含停用项。
     */
    @GetMapping("/{id}/items")
    @PreAuthorize("hasAuthority('system:dictionary:read')")
    @Operation(
        summary = "按字典 ID 查询全部字典项",
        description = "需要 `system:dictionary:read` 权限。包含已停用的字典项，按排序值和 ID 升序排列。",
        parameters = [Parameter(name = "id", `in` = ParameterIn.PATH, description = "字典 ID")],
        responses = [
            ApiResponse(responseCode = "200", description = "字典项列表", useReturnTypeSchema = true),
            ApiResponse(responseCode = "404", description = "字典不存在", content = [Content()]),
        ],
    )
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
    @Operation(
        summary = "创建字典",
        description = "需要 `$DICTIONARY_WRITE` 权限。字典编码全局唯一，创建后固定。",
        responses = [
            ApiResponse(responseCode = "201", description = "字典已创建", useReturnTypeSchema = true),
            ApiResponse(responseCode = "400", description = "字典编码或名称无效", content = [Content()]),
            ApiResponse(responseCode = "409", description = "字典编码已存在", content = [Content()]),
        ],
    )
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
    @Operation(
        summary = "更新字典名称和启用状态",
        description = "需要 `$DICTIONARY_WRITE` 权限。停用后，按编码查询的接口将返回 404。",
        parameters = [Parameter(name = "id", `in` = ParameterIn.PATH, description = "字典 ID")],
        responses = [
            ApiResponse(responseCode = "204", description = "字典已更新", content = [Content()]),
            ApiResponse(responseCode = "400", description = "字典名称无效或缺少启用状态", content = [Content()]),
            ApiResponse(responseCode = "404", description = "字典不存在", content = [Content()]),
        ],
    )
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
    @Operation(
        summary = "创建字典项",
        description = "需要 `$DICTIONARY_WRITE` 权限。项值在同一字典内唯一。",
        parameters = [Parameter(name = "id", `in` = ParameterIn.PATH, description = "所属字典 ID")],
        responses = [
            ApiResponse(responseCode = "201", description = "字典项已创建", useReturnTypeSchema = true),
            ApiResponse(responseCode = "400", description = "字典项值或标签无效", content = [Content()]),
            ApiResponse(responseCode = "404", description = "字典不存在", content = [Content()]),
            ApiResponse(responseCode = "409", description = "同一字典中已存在相同的项值", content = [Content()]),
        ],
    )
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
    @Operation(
        summary = "更新字典项",
        description = "需要 `$DICTIONARY_WRITE` 权限。更新项值、标签、排序值和启用状态。",
        responses = [
            ApiResponse(responseCode = "204", description = "字典项已更新", content = [Content()]),
            ApiResponse(responseCode = "400", description = "字典项值或标签无效", content = [Content()]),
            ApiResponse(responseCode = "404", description = "字典或该字典下的字典项不存在", content = [Content()]),
            ApiResponse(responseCode = "409", description = "同一字典中已存在相同的项值", content = [Content()]),
        ],
    )
    fun updateItem(
        @Parameter(description = "所属字典 ID")
        @PathVariable dictionaryId: String,
        @Parameter(description = "字典项 ID")
        @PathVariable id: String,
        @RequestBody request: DictionaryItemRequest,
    ) = dictionaries.updateItem(dictionaryId, id, request)

    /** 删除单个没有字典项的字典。 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$DICTIONARY_WRITE')")
    @Operation(
        summary = "删除字典",
        description = "需要内置管理员身份和 `$DICTIONARY_WRITE` 权限。删除前须清空字典项。",
        responses = [
            ApiResponse(responseCode = "204", description = "字典已删除", content = [Content()]),
            ApiResponse(responseCode = "404", description = "字典不存在", content = [Content()]),
            ApiResponse(responseCode = "409", description = "字典仍包含字典项", content = [Content()]),
        ],
    )
    fun delete(@PathVariable id: String) = dictionaries.delete(listOf(id))

    /** 原子删除多个没有字典项的字典。 */
    @PostMapping("/batch-delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$DICTIONARY_WRITE')")
    @Operation(
        summary = "批量删除字典",
        description = "需要内置管理员身份和 `$DICTIONARY_WRITE` 权限。任一字典无法删除时整批回滚。",
        responses = [
            ApiResponse(responseCode = "204", description = "指定字典均已删除", content = [Content()]),
            ApiResponse(responseCode = "400", description = "ID 列表无效", content = [Content()]),
            ApiResponse(responseCode = "404", description = "有字典不存在", content = [Content()]),
            ApiResponse(responseCode = "409", description = "有字典仍包含字典项", content = [Content()]),
        ],
    )
    fun deleteBatch(@RequestBody request: DeleteResourcesRequest) = dictionaries.delete(request.ids)

    /** 删除指定字典中的一个选项。 */
    @DeleteMapping("/{dictionaryId}/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$DICTIONARY_WRITE')")
    @Operation(
        summary = "删除字典项",
        description = "需要内置管理员身份和 `$DICTIONARY_WRITE` 权限。",
        responses = [
            ApiResponse(responseCode = "204", description = "字典项已删除", content = [Content()]),
            ApiResponse(responseCode = "404", description = "字典或该字典下的指定字典项不存在", content = [Content()]),
        ],
    )
    fun deleteItem(@PathVariable dictionaryId: String, @PathVariable id: String) =
        dictionaries.deleteItems(dictionaryId, listOf(id))

    /** 原子删除指定字典中的多个选项。 */
    @PostMapping("/{dictionaryId}/items/batch-delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$DICTIONARY_WRITE')")
    @Operation(
        summary = "批量删除字典项",
        description = "需要内置管理员身份和 `$DICTIONARY_WRITE` 权限。字典项须属于同一个字典，任一项无法删除时整批回滚。",
        responses = [
            ApiResponse(responseCode = "204", description = "指定字典项均已删除", content = [Content()]),
            ApiResponse(responseCode = "400", description = "ID 列表无效", content = [Content()]),
            ApiResponse(responseCode = "404", description = "字典不存在或有字典项不属于该字典", content = [Content()]),
        ],
    )
    fun deleteItemBatch(@PathVariable dictionaryId: String, @RequestBody request: DeleteResourcesRequest) =
        dictionaries.deleteItems(dictionaryId, request.ids)
}
