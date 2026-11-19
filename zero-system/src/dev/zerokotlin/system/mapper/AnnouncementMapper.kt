package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.InboxEntry
import dev.zerokotlin.system.entity.AnnouncementRecord
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

/** 公告及用户收件箱已读记录的数据访问接口。 */
@Mapper
interface AnnouncementMapper : BaseMapper<AnnouncementRecord> {
    /**
     * 分页查询已发布的公告，并附上指定用户的已读时间。
     *
     * @param userId 用于匹配已读记录的用户 ID。
     * @param unreadOnly 是否仅返回该用户尚未标记已读的公告。
     * @param window 当前页的条数和偏移量。
     * @return 当前页的公告，按发布时间和公告 ID 依次降序排列。
     */
    fun findInbox(
        @Param("userId") userId: String,
        @Param("unreadOnly") unreadOnly: Boolean,
        @Param("window") window: PageWindow,
    ): List<InboxEntry>

    /**
     * 使用与 [findInbox] 相同的筛选条件统计已发布公告的总数。
     *
     * @param userId 用于匹配已读记录的用户 ID。
     * @param unreadOnly 是否仅统计该用户尚未标记已读的公告。
     * @return 应用筛选后、分页前的公告条数。
     */
    fun countInbox(@Param("userId") userId: String, @Param("unreadOnly") unreadOnly: Boolean): Long

    /**
     * 确认公告当前处于已发布状态。
     *
     * @param id 要检查的公告 ID。
     * @return 公告存在且已发布时返回其 ID，否则返回 `null`。
     */
    @Select("SELECT id FROM sys_announcement WHERE id = #{id} AND status = 'PUBLISHED'")
    fun findPublishedId(@Param("id") id: String): String?

    /**
     * 查询指定用户对某条公告的已读记录。
     *
     * @param userId 查阅公告的用户 ID。
     * @param id 公告 ID。
     * @return 首次标记已读的 Unix 毫秒时间戳，无已读记录时返回 `null`。
     */
    @Select("SELECT read_at FROM sys_announcement_read WHERE user_id = #{userId} AND announcement_id = #{id}")
    fun findReadAt(@Param("userId") userId: String, @Param("id") id: String): Long?

    /**
     * 写入首次已读记录。同一用户和公告只允许一条已读记录。
     *
     * @param userId 标记已读的用户 ID。
     * @param id 被标记的公告 ID。
     * @param now 标记已读的时间，单位为 Unix 毫秒。
     */
    @Insert("INSERT INTO sys_announcement_read (user_id, announcement_id, read_at) VALUES (#{userId}, #{id}, #{now})")
    fun insertRead(@Param("userId") userId: String, @Param("id") id: String, @Param("now") now: Long)
}
