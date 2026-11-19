package dev.zerokotlin.core.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 写入 Elasticsearch 的文档内容。文档 ID 由请求路径传入。
 *
 * @property title 文档标题，对应索引的 `title` 字段。
 * @property content 文档正文，对应索引的 `content` 字段。
 */
@Schema(description = "写入 Elasticsearch 的文档内容。文档 ID 由请求路径传入。")
data class SearchDocument(
    @field:Schema(description = "文档标题，对应索引的 `title` 字段。")
    val title: String,
    @field:Schema(description = "文档正文，对应索引的 `content` 字段。")
    val content: String,
)
