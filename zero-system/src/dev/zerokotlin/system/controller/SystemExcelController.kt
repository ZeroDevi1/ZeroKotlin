package dev.zerokotlin.system.controller

import dev.zerokotlin.core.excel.XLSX_MEDIA_TYPE
import dev.zerokotlin.system.dto.ExcelImportResult
import dev.zerokotlin.system.security.DICTIONARY_READ
import dev.zerokotlin.system.security.DICTIONARY_WRITE
import dev.zerokotlin.system.security.USER_MANAGE
import dev.zerokotlin.system.security.USER_READ
import dev.zerokotlin.system.service.SystemExcel
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/** 提供用户、字典和字典项的 XLSX 模板下载、原子导入与导出接口。 */
@RestController
@RequestMapping("/api/system")
@Tag(name = "Excel 导入导出")
class SystemExcelController(private val excel: SystemExcel) {
    /** 为有权创建账号的内置管理员提供空白模板。 */
    @GetMapping("/users/excel/template", produces = [XLSX_MEDIA_TYPE])
    @PreAuthorize("hasAuthority('$USER_MANAGE')")
    @Operation(summary = "下载用户导入模板")
    @ApiResponse(responseCode = "200", description = "XLSX 模板",
        content = [Content(mediaType = XLSX_MEDIA_TYPE, schema = Schema(type = "string", format = "binary"))])
    fun userTemplate(): ResponseEntity<ByteArray> = download("用户导入模板.xlsx", excel.userTemplate())

    /** 批量创建新账号，密码、部门和业务角色沿用账号创建规则。 */
    @PostMapping("/users/excel/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$USER_MANAGE')")
    @Operation(
        summary = "从 Excel 新增用户",
        description = "仅内置管理员可调用。上传 file 字段，限 1 MiB、100 条数据行。须逐行填写初始密码，整批提交。",
        responses = [
            ApiResponse(responseCode = "201", description = "用户导入成功", useReturnTypeSchema = true),
            ApiResponse(responseCode = "400", description = "模板或账号数据无效", content = [Content()]),
            ApiResponse(responseCode = "409", description = "用户名冲突", content = [Content()]),
            ApiResponse(responseCode = "413", description = "超出文件大小限制", content = [Content()]),
        ],
    )
    fun importUsers(@RequestPart("file") file: MultipartFile): ExcelImportResult = excel.importUsers(file)

    /** 下载现有读取权限及筛选条件下的全部匹配账号，最多 1000 条。 */
    @GetMapping("/users/excel/export", produces = [XLSX_MEDIA_TYPE])
    @PreAuthorize("hasAuthority('$USER_READ')")
    @Operation(summary = "导出可见用户", description = "沿用用户列表的行级授权、部门和角色筛选。最多导出 1000 条。")
    @ApiResponse(responseCode = "200", description = "XLSX 文件",
        content = [Content(mediaType = XLSX_MEDIA_TYPE, schema = Schema(type = "string", format = "binary"))])
    @ApiResponse(responseCode = "400", description = "超出导出行数限制", content = [Content()])
    fun exportUsers(
        @Parameter(description = "直属部门 ID") @RequestParam(required = false) departmentId: String?,
        @Parameter(description = "已分配的角色 ID") @RequestParam(required = false) roleId: String?,
    ): ResponseEntity<ByteArray> = download("用户.xlsx", excel.exportUsers(departmentId, roleId))

    /** 下载用于新增字典的空白模板。 */
    @GetMapping("/dictionaries/excel/template", produces = [XLSX_MEDIA_TYPE])
    @PreAuthorize("hasAuthority('$DICTIONARY_WRITE')")
    @Operation(summary = "下载字典导入模板")
    @ApiResponse(responseCode = "200", description = "XLSX 模板",
        content = [Content(mediaType = XLSX_MEDIA_TYPE, schema = Schema(type = "string", format = "binary"))])
    fun dictionaryTemplate(): ResponseEntity<ByteArray> = download("字典导入模板.xlsx", excel.dictionaryTemplate())

    /** 批量新增启用的字典，编码重复时整批回滚。 */
    @PostMapping("/dictionaries/excel/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$DICTIONARY_WRITE')")
    @Operation(
        summary = "从 Excel 新增字典",
        description = "上传 file 字段，限 1 MiB、1000 条数据行。沿用字典创建规则，整批提交。",
        responses = [
            ApiResponse(responseCode = "201", description = "字典导入成功", useReturnTypeSchema = true),
            ApiResponse(responseCode = "400", description = "模板或字典数据无效", content = [Content()]),
            ApiResponse(responseCode = "409", description = "字典编码冲突", content = [Content()]),
            ApiResponse(responseCode = "413", description = "超出文件大小限制", content = [Content()]),
        ],
    )
    fun importDictionaries(@RequestPart("file") file: MultipartFile): ExcelImportResult = excel.importDictionaries(file)

    /** 导出全部启用和停用的字典，最多 1000 条。 */
    @GetMapping("/dictionaries/excel/export", produces = [XLSX_MEDIA_TYPE])
    @PreAuthorize("hasAuthority('$DICTIONARY_READ')")
    @Operation(summary = "导出字典", description = "包含停用字典，最多导出 1000 条。")
    @ApiResponse(responseCode = "200", description = "XLSX 文件",
        content = [Content(mediaType = XLSX_MEDIA_TYPE, schema = Schema(type = "string", format = "binary"))])
    @ApiResponse(responseCode = "400", description = "超出导出行数限制", content = [Content()])
    fun exportDictionaries(): ResponseEntity<ByteArray> = download("字典.xlsx", excel.exportDictionaries())

    /** 下载指定字典的字典项模板。 */
    @GetMapping("/dictionaries/{dictionaryId}/items/excel/template", produces = [XLSX_MEDIA_TYPE])
    @PreAuthorize("hasAuthority('$DICTIONARY_WRITE')")
    @Operation(summary = "下载字典项导入模板")
    @ApiResponse(responseCode = "200", description = "XLSX 模板",
        content = [Content(mediaType = XLSX_MEDIA_TYPE, schema = Schema(type = "string", format = "binary"))])
    @ApiResponse(responseCode = "404", description = "字典不存在", content = [Content()])
    fun itemTemplate(@PathVariable dictionaryId: String): ResponseEntity<ByteArray> =
        download("字典项导入模板.xlsx", excel.itemTemplate(dictionaryId))

    /** 批量在指定字典下新增字典项，值重复时整批回滚。 */
    @PostMapping("/dictionaries/{dictionaryId}/items/excel/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$DICTIONARY_WRITE')")
    @Operation(
        summary = "从 Excel 新增字典项",
        description = "上传 file 字段，限 1 MiB、1000 条数据行。全部记录写入路径所指定的字典，整批提交。",
        responses = [
            ApiResponse(responseCode = "201", description = "字典项导入成功", useReturnTypeSchema = true),
            ApiResponse(responseCode = "400", description = "模板或字典项数据无效", content = [Content()]),
            ApiResponse(responseCode = "404", description = "字典不存在", content = [Content()]),
            ApiResponse(responseCode = "409", description = "同一字典下的值冲突", content = [Content()]),
            ApiResponse(responseCode = "413", description = "超出文件大小限制", content = [Content()]),
        ],
    )
    fun importItems(@PathVariable dictionaryId: String, @RequestPart("file") file: MultipartFile): ExcelImportResult =
        excel.importItems(dictionaryId, file)

    /** 下载指定字典的全部启用和停用字典项，最多 1000 条。 */
    @GetMapping("/dictionaries/{dictionaryId}/items/excel/export", produces = [XLSX_MEDIA_TYPE])
    @PreAuthorize("hasAuthority('$DICTIONARY_READ')")
    @Operation(summary = "导出字典项", description = "仅导出路径所指定字典中的条目，含停用项，最多 1000 条。")
    @ApiResponse(responseCode = "200", description = "XLSX 文件",
        content = [Content(mediaType = XLSX_MEDIA_TYPE, schema = Schema(type = "string", format = "binary"))])
    @ApiResponse(responseCode = "400", description = "超出导出行数限制", content = [Content()])
    @ApiResponse(responseCode = "404", description = "字典不存在", content = [Content()])
    fun exportItems(@PathVariable dictionaryId: String): ResponseEntity<ByteArray> =
        download("字典项.xlsx", excel.exportItems(dictionaryId))

    /** 使用 UTF-8 文件名发回附件，并禁止缓存。 */
    private fun download(filename: String, bytes: ByteArray): ResponseEntity<ByteArray> =
        ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename, Charsets.UTF_8).build().toString())
            .cacheControl(CacheControl.noStore())
            .body(bytes)
}
