package dev.zerokotlin.system.service

import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.AnnouncementEntry
import dev.zerokotlin.system.dto.AnnouncementRequest
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.InboxEntry
import dev.zerokotlin.system.entity.AnnouncementRecord
import dev.zerokotlin.system.entity.AnnouncementStatus
import dev.zerokotlin.system.mapper.AnnouncementMapper
import dev.zerokotlin.system.security.ANNOUNCEMENT_WRITE
import dev.zerokotlin.system.security.currentSystemActor
import dev.zerokotlin.system.support.checkedText
import dev.zerokotlin.system.support.conflict
import dev.zerokotlin.system.support.missing
import dev.zerokotlin.system.support.newId
import dev.zerokotlin.system.support.query
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val ANNOUNCEMENT_BODY_MAX_LENGTH = 16_000

@Service
class SystemAnnouncements(
    private val announcements: AnnouncementMapper,
    private val writes: SystemWrites,
) {
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

    @Transactional
    fun create(request: AnnouncementRequest): CreatedResource {
        val actor = writes.authorize(ANNOUNCEMENT_WRITE)
        val now = System.currentTimeMillis()
        val announcement = AnnouncementRecord(
            id = newId(),
            title = checkedText(request.title, "title"),
            body = checkedText(request.body, "body", ANNOUNCEMENT_BODY_MAX_LENGTH),
            createdAt = now,
            updatedAt = now,
        )
        announcements.insert(announcement)
        writes.record(actor, "announcement.create", announcement.id)
        return CreatedResource(announcement.id)
    }

    @Transactional
    fun update(id: String, request: AnnouncementRequest) {
        val actor = writes.authorize(ANNOUNCEMENT_WRITE)
        val announcement = announcements.selectById(id) ?: missing("公告")
        if (announcement.status != AnnouncementStatus.DRAFT.name) conflict("仅草稿公告可以修改")

        announcement.title = checkedText(request.title, "title")
        announcement.body = checkedText(request.body, "body", ANNOUNCEMENT_BODY_MAX_LENGTH)
        announcement.updatedAt = System.currentTimeMillis()
        announcements.updateById(announcement)
        writes.record(actor, "announcement.update", announcement.id)
    }

    @Transactional
    fun publish(id: String) =
        transition(id, AnnouncementStatus.DRAFT, AnnouncementStatus.PUBLISHED, "announcement.publish")

    @Transactional
    fun revoke(id: String) =
        transition(id, AnnouncementStatus.PUBLISHED, AnnouncementStatus.REVOKED, "announcement.revoke")

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

@Service
class SystemInbox(private val announcements: AnnouncementMapper, private val writes: SystemWrites) {
    @Transactional(readOnly = true)
    fun list(window: PageWindow, unreadOnly: Boolean): PageResult<InboxEntry> {
        val userId = currentSystemActor().user.id
        val entries = announcements.findInbox(userId, unreadOnly, window)
        return window.result(entries, announcements.countInbox(userId, unreadOnly))
    }

    @Transactional
    fun markRead(id: String) {
        val userId = writes.lockAndAuthenticate().user.id
        val announcementId = announcements.findPublishedId(id) ?: missing("公告")
        if (announcements.findReadAt(userId, announcementId) == null) {
            announcements.insertRead(userId, announcementId, System.currentTimeMillis())
        }
    }
}
