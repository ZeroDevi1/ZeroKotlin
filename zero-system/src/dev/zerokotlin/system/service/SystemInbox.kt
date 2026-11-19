package dev.zerokotlin.system.service

import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.InboxEntry
import dev.zerokotlin.system.mapper.AnnouncementMapper
import dev.zerokotlin.system.security.currentSystemActor
import dev.zerokotlin.system.support.missing
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 提供按用户区分已读状态的已发布公告收件箱。
 *
 * @property announcements 查询可见公告和读标记，保存首次阅读时间。
 * @property writes 标记已读时串行化写入并重新认证当前会话。
 */
@Service
class SystemInbox(private val announcements: AnnouncementMapper, private val writes: SystemWrites) {
    /**
     * 在只读事务中分页查询当前用户的收件箱。
     *
     * @param window 页码和每页条数。
     * @param unreadOnly 为 true 时仅匹配该用户没有已读记录的公告。
     * @return 按发布时间和公告 ID 倒序排列的一页已发布公告及匹配总数。
     */
    @Transactional(readOnly = true)
    fun list(window: PageWindow, unreadOnly: Boolean): PageResult<InboxEntry> {
        val userId = currentSystemActor().user.id
        val entries = announcements.findInbox(userId, unreadOnly, window)
        return window.result(entries, announcements.countInbox(userId, unreadOnly))
    }

    /**
     * 重新认证当前用户并为已发布公告保存首次阅读时间。重复标记会保留原时间。
     *
     * @param id 待标记的公告 ID。目标不存在或当前未发布时返回 404。
     */
    @Transactional
    fun markRead(id: String) {
        val userId = writes.lockAndAuthenticate().user.id
        val announcementId = announcements.findPublishedId(id) ?: missing("公告")
        if (announcements.findReadAt(userId, announcementId) == null) {
            announcements.insertRead(userId, announcementId, System.currentTimeMillis())
        }
    }

    /** 在系统写入锁内重新认证当前会话，为所有已发布的未读公告记录同一已读时间。 */
    @Transactional
    fun markAllRead() {
        val userId = writes.lockAndAuthenticate().user.id
        announcements.insertUnreadAsRead(userId, System.currentTimeMillis())
    }
}
