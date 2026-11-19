package dev.zerokotlin.system.controller

import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.FileEntry
import dev.zerokotlin.system.security.FILE_DELETE
import dev.zerokotlin.system.security.FILE_DOWNLOAD
import dev.zerokotlin.system.security.FILE_READ
import dev.zerokotlin.system.security.FILE_UPLOAD
import dev.zerokotlin.system.service.SystemFileService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/** 提供受 Bearer 会话保护的文件目录、上传、附件下载和删除接口。 */
@RestController
@RequestMapping("/api/system/files")
@Tag(name = "文件管理")
class SystemFileController(private val files: SystemFileService) {
    /** 分页查询文件元数据及当前状态，支持文件名和业务分类筛选。 */
    @GetMapping
    @PreAuthorize("hasAuthority('$FILE_READ')")
    @Operation(summary = "分页查询文件", description = "需要 `$FILE_READ` 权限。按登记时间和 ID 倒序排列。")
    fun list(
        @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数，范围为 1–100") @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "文件名片段") @RequestParam(required = false) name: String?,
        @Parameter(description = "业务分类精确编码") @RequestParam(required = false) bizType: String?,
    ): PageResult<FileEntry> = files.list(PageWindow(page, size), name, bizType)

    /** 上传一个文件和可选业务分类，返回已发布的元数据。 */
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$FILE_UPLOAD')")
    @Operation(
        summary = "上传文件",
        description = "需要 `$FILE_UPLOAD` 权限。内容写入部署配置的默认后端。",
        responses = [
            ApiResponse(responseCode = "201", description = "文件已上传", useReturnTypeSchema = true),
            ApiResponse(responseCode = "400", description = "文件名或业务分类无效，或缺少文件", content = [Content()]),
            ApiResponse(responseCode = "413", description = "上传超过部署配置上限", content = [Content()]),
            ApiResponse(responseCode = "503", description = "文件存储暂不可用", content = [Content()]),
        ],
    )
    fun upload(
        @RequestPart("file") file: MultipartFile,
        @Parameter(description = "可选的业务分类编码", example = "documents")
        @RequestPart("bizType", required = false) bizType: String?,
    ): FileEntry = files.upload(file, bizType)

    /** 下载可用文件。响应以原文件名强制作为附件，并且禁止缓存。 */
    @GetMapping("/{id}/download", produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    @PreAuthorize("hasAuthority('$FILE_DOWNLOAD')")
    @Operation(
        summary = "下载文件",
        description = "需要 `$FILE_DOWNLOAD` 权限。返回二进制附件。",
        responses = [
            ApiResponse(responseCode = "200", description = "文件内容", content = [
                Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE, schema = Schema(type = "string", format = "binary")),
            ]),
            ApiResponse(responseCode = "404", description = "没有可下载的文件内容", content = [Content()]),
            ApiResponse(responseCode = "503", description = "文件存储暂不可用", content = [Content()]),
        ],
    )
    fun download(@PathVariable id: UUID, response: HttpServletResponse) {
        files.download(id) { file, input ->
            response.contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE
            response.setContentLengthLong(file.sizeBytes)
            val attachment = ContentDisposition.attachment().filename(file.fileName, UTF_8).build()
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, attachment.toString())
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
            response.setHeader("X-Content-Type-Options", "nosniff")
            input.copyTo(response.outputStream)
        }
    }

    /** 删除文件。允许重试，已完成的删除返回 HTTP 204。 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$FILE_DELETE')")
    @Operation(
        summary = "删除文件",
        description = "需要 `$FILE_DELETE` 权限。对象存储不可用时保留待删除记录供重试。",
        responses = [
            ApiResponse(responseCode = "204", description = "文件已删除", content = [Content()]),
            ApiResponse(responseCode = "409", description = "文件仍在上传", content = [Content()]),
            ApiResponse(responseCode = "503", description = "文件存储暂不可用", content = [Content()]),
        ],
    )
    fun delete(@PathVariable id: UUID) = files.delete(id)
}
