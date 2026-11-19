package dev.zerokotlin.system.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 创建或修改公告草稿时提交的内容。
 *
 * @property title 公告标题，去除首尾空白后须非空，最多 120 字符。
 * @property body 公告正文，去除首尾空白后须非空，最多 16 000 字符。
 */
@Schema(description = "创建或修改公告草稿时提交的内容。")
data class AnnouncementRequest(
    @field:Schema(description = "公告标题，去除首尾空白后须非空，最多 120 字符。")
    val title: String,
    @field:Schema(description = "公告正文，去除首尾空白后须非空，最多 16 000 字符。")
    val body: String,
)

/**
 * 公告管理列表中的一条记录，时间均为 Unix 毫秒时间戳。
 *
 * @property id 公告 ID。
 * @property title 公告标题。
 * @property body 公告正文。
 * @property status 公告状态，取值为 `DRAFT`、`PUBLISHED` 或 `REVOKED`。
 * @property createdAt 草稿创建时间。
 * @property updatedAt 最近一次修改内容或切换状态的时间。
 * @property publishedAt 发布时间，草稿为 `null`，撤回后仍保留。
 */
@Schema(description = "公告管理列表中的一条记录，时间均为 Unix 毫秒时间戳。")
data class AnnouncementEntry(
    @field:Schema(description = "公告 ID。")
    val id: String,
    @field:Schema(description = "公告标题。")
    val title: String,
    @field:Schema(description = "公告正文。")
    val body: String,
    @field:Schema(
        description = "公告状态，取值为 `DRAFT`、`PUBLISHED` 或 `REVOKED`。",
        allowableValues = ["DRAFT", "PUBLISHED", "REVOKED"],
    )
    val status: String,
    @field:Schema(
        description = "Unix 毫秒时间戳。草稿创建时间。",
        type = "integer",
        format = "int64",
        example = "1767225600000",
    )
    val createdAt: Long,
    @field:Schema(
        description = "Unix 毫秒时间戳。最近一次修改内容或切换状态的时间。",
        type = "integer",
        format = "int64",
        example = "1767225600000",
    )
    val updatedAt: Long,
    @field:Schema(
        description = "Unix 毫秒时间戳。发布时间，草稿为 `null`，撤回后仍保留。",
        type = "integer",
        format = "int64",
        example = "1767225600000",
    )
    val publishedAt: Long?,
)

/**
 * 用户收件箱中已发布的一条公告，时间均为 Unix 毫秒时间戳。
 *
 * @property id 公告 ID。
 * @property title 公告标题。
 * @property body 公告正文。
 * @property publishedAt 公告发布时间。
 * @property readAt 当前用户首次标记已读的时间，未读时为 `null`。
 */
@Schema(description = "用户收件箱中已发布的一条公告，时间均为 Unix 毫秒时间戳。")
data class InboxEntry(
    @field:Schema(description = "公告 ID。")
    val id: String,
    @field:Schema(description = "公告标题。")
    val title: String,
    @field:Schema(description = "公告正文。")
    val body: String,
    @field:Schema(
        description = "Unix 毫秒时间戳。公告发布时间。",
        type = "integer",
        format = "int64",
        example = "1767225600000",
    )
    val publishedAt: Long,
    @field:Schema(
        description = "Unix 毫秒时间戳。当前用户首次标记已读的时间，未读时为 `null`。",
        type = "integer",
        format = "int64",
        example = "1767225600000",
    )
    val readAt: Long?,
)
