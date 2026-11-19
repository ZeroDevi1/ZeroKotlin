package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.InboxEntry
import dev.zerokotlin.system.entity.AnnouncementRecord
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

@Mapper
interface AnnouncementMapper : BaseMapper<AnnouncementRecord> {
    fun findInbox(
        @Param("userId") userId: String,
        @Param("unreadOnly") unreadOnly: Boolean,
        @Param("window") window: PageWindow,
    ): List<InboxEntry>

    fun countInbox(@Param("userId") userId: String, @Param("unreadOnly") unreadOnly: Boolean): Long

    @Select("SELECT id FROM sys_announcement WHERE id = #{id} AND status = 'PUBLISHED'")
    fun findPublishedId(@Param("id") id: String): String?

    @Select("SELECT read_at FROM sys_announcement_read WHERE user_id = #{userId} AND announcement_id = #{id}")
    fun findReadAt(@Param("userId") userId: String, @Param("id") id: String): Long?

    @Insert("INSERT INTO sys_announcement_read (user_id, announcement_id, read_at) VALUES (#{userId}, #{id}, #{now})")
    fun insertRead(@Param("userId") userId: String, @Param("id") id: String, @Param("now") now: Long)
}
