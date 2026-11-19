package dev.zerokotlin.system.service

import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.core.security.PlainTextInput
import dev.zerokotlin.system.dto.AnnouncementEntry
import dev.zerokotlin.system.dto.AnnouncementRequest
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.InboxEntry
import dev.zerokotlin.system.entity.AnnouncementRecord
import dev.zerokotlin.system.entity.AnnouncementStatus
import dev.zerokotlin.system.mapper.AnnouncementMapper
import dev.zerokotlin.system.security.ANNOUNCEMENT_WRITE
import dev.zerokotlin.system.security.currentSystemActor
import dev.zerokotlin.system.support.checkedPlainText
import dev.zerokotlin.system.support.checkedText
import dev.zerokotlin.system.support.conflict
import dev.zerokotlin.system.support.missing
import dev.zerokotlin.system.support.newId
import dev.zerokotlin.system.support.query
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 公告正文去除首尾空白后的长度上限，单位为 UTF-16 代码单元。 */
private const val ANNOUNCEMENT_BODY_MAX_LENGTH = 16_000

/**
 * 管理公告草稿、发布和撤回。状态变更与对应的操作审计在同一事务提交。
 *
 * @property announcements 读写公告及其状态。
 * @property writes 串行化写入，重新校验公告写入权限并记录审计。
 * @property plainText 可启用的公告标题清理器。
 */
@Service
class SystemAnnouncements(
    private val announcements: AnnouncementMapper,
    private val writes: SystemWrites,
    private val plainText: PlainTextInput,
) {
    /**
     * 在只读事务中查询管理用公告列表。
     *
     * @param window 页码和每页条数。
     * @return 按创建时间和 ID 倒序排列的公告页，包含各状态的记录。
     */
    @Transactional(readOnly = true)
    fun list(window: PageWindow): PageResult<AnnouncementEntry> {
        val results = announcements.selectPage(
            Page<AnnouncementRecord>(window.page.toLong(), window.size.toLong()),
            query<AnnouncementRecord>().orderByDesc("created_at", "id"),
        )
        val entries = results.records.map {
            AnnouncementEntry(it.id, it.title, it.body, it.status, it.createdAt, it.updatedAt, it.publishedAt)
        }
        return window.result(entries, results.total)
    }

    /**
     * 校验公告写入权限及内容后创建草稿，并记录审计。
     *
     * @param request 草稿的标题和正文，均须包含非空白文本。
     * @return 新公告 ID。
     */
    @Transactional
    fun create(request: AnnouncementRequest): CreatedResource {
        val actor = writes.authorize(ANNOUNCEMENT_WRITE)
        val now = System.currentTimeMillis()
        val announcement = AnnouncementRecord(
            id = newId(),
            title = plainText.checkedPlainText(request.title, "title"),
            body = checkedText(request.body, "body", ANNOUNCEMENT_BODY_MAX_LENGTH),
            createdAt = now,
            updatedAt = now,
        )
        announcements.insert(announcement)
        writes.record(actor, "announcement.create", announcement.id)
        return CreatedResource(announcement.id)
    }

    /**
     * 修改草稿内容并刷新更新时间。公告不存在时返回 404，草稿以外的状态返回 409。
     *
     * @param id 待修改的公告 ID。
     * @param request 草稿的新标题和正文。
     */
    @Transactional
    fun update(id: String, request: AnnouncementRequest) {
        val actor = writes.authorize(ANNOUNCEMENT_WRITE)
        val announcement = announcements.selectById(id) ?: missing("公告")
        if (announcement.status != AnnouncementStatus.DRAFT.name) conflict("仅草稿公告可以修改")

        announcement.title = plainText.checkedPlainText(request.title, "title")
        announcement.body = checkedText(request.body, "body", ANNOUNCEMENT_BODY_MAX_LENGTH)
        announcement.updatedAt = System.currentTimeMillis()
        announcements.updateById(announcement)
        writes.record(actor, "announcement.update", announcement.id)
    }

    /**
     * 将草稿转为已发布状态，同时记录发布时间。草稿以外的状态返回 409。
     *
     * @param id 待发布的公告 ID。
     */
    @Transactional
    fun publish(id: String) =
        transition(id, AnnouncementStatus.DRAFT, AnnouncementStatus.PUBLISHED, "announcement.publish")

    /**
     * 将已发布公告转为已撤回状态。已发布以外的状态返回 409。
     *
     * @param id 待撤回的公告 ID。
     */
    @Transactional
    fun revoke(id: String) =
        transition(id, AnnouncementStatus.PUBLISHED, AnnouncementStatus.REVOKED, "announcement.revoke")

    /**
     * 校验公告写入权限并执行状态迁移。迁移总会刷新更新时间，进入已发布状态时同时设置发布时间。
     *
     * @param id 待变更的公告 ID，不存在时返回 404。
     * @param from 允许执行迁移的当前状态，不匹配时返回 409。
     * @param to 迁移后的状态。
     * @param action 写入操作审计的动作标识。
     */
    private fun transition(id: String, from: AnnouncementStatus, to: AnnouncementStatus, action: String) {
        val actor = writes.authorize(ANNOUNCEMENT_WRITE)
        val announcement = announcements.selectById(id) ?: missing("公告")
        if (announcement.status != from.name) conflict("当前公告状态不允许此操作")

        val now = System.currentTimeMillis()
        announcement.status = to.name
        announcement.updatedAt = now
        if (to == AnnouncementStatus.PUBLISHED) announcement.publishedAt = now
        announcements.updateById(announcement)
        writes.record(actor, action, announcement.id)
    }
}

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
