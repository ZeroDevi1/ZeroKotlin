package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

/**
 * `sys_announcement` 中的公告及其发布状态，时间均为 Unix 毫秒时间戳。
 *
 * @property id 公告主键。
 * @property title 公告标题。
 * @property body 公告正文。
 * @property status [AnnouncementStatus] 的枚举名，新建时为 `DRAFT`。
 * @property createdAt 草稿创建时间。
 * @property updatedAt 最近一次修改内容或切换状态的时间。
 * @property publishedAt 公告发布时间，草稿为 `null`，撤回后仍保留。
 */
@TableName("sys_announcement")
class AnnouncementRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    var title: String = "",
    var body: String = "",
    var status: String = AnnouncementStatus.DRAFT.name,
    var createdAt: Long = 0,
    var updatedAt: Long = 0,
    var publishedAt: Long? = null,
)
