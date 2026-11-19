package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

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
