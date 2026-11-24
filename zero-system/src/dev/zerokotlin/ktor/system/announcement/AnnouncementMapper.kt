package dev.zerokotlin.ktor.system.announcement

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.baomidou.mybatisplus.core.mapper.BaseMapper

/** publishedAt 为空表示未发布；撤回时保留先前的发布时间。 */
@TableName("sys_announcement")
internal class AnnouncementRecord {
    @field:TableId(type = IdType.INPUT)
    var id: String? = null
    var title: String? = null
    var body: String? = null
    var status: String? = null
    @field:TableField("created_at")
    var createdAt: Long? = null
    @field:TableField("updated_at")
    var updatedAt: Long? = null
    @field:TableField("published_at")
    var publishedAt: Long? = null

    fun summary() = Announcement(
        checkNotNull(id), checkNotNull(title), checkNotNull(body), checkNotNull(status),
        checkNotNull(createdAt), checkNotNull(updatedAt), publishedAt,
    )
}

internal interface AnnouncementMapper : BaseMapper<AnnouncementRecord>
