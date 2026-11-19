package dev.zerokotlin.system

import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

const val ANNOUNCEMENT_READ = "system:announcement:read"
const val ANNOUNCEMENT_WRITE = "system:announcement:write"
private const val ANNOUNCEMENT_BODY_MAX_LENGTH = 16_000

enum class AnnouncementStatus { DRAFT, PUBLISHED, REVOKED }

data class AnnouncementRequest(val title: String, val body: String)

data class AnnouncementEntry(
    val id: String,
    val title: String,
    val body: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val publishedAt: Long?,
)

data class InboxEntry(val id: String, val title: String, val body: String, val publishedAt: Long, val readAt: Long?)

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

@RestController
@RequestMapping("/api/system/announcements")
class SystemAnnouncementController(private val announcements: SystemAnnouncements) {
    @GetMapping
    @PreAuthorize("hasAuthority('$ANNOUNCEMENT_READ')")
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResult<AnnouncementEntry> = announcements.list(PageWindow(page, size))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$ANNOUNCEMENT_WRITE')")
    fun create(@RequestBody request: AnnouncementRequest): CreatedResource = announcements.create(request)

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$ANNOUNCEMENT_WRITE')")
    fun update(@PathVariable id: String, @RequestBody request: AnnouncementRequest) = announcements.update(id, request)

    @PostMapping("/{id}/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$ANNOUNCEMENT_WRITE')")
    fun publish(@PathVariable id: String) = announcements.publish(id)

    @PostMapping("/{id}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$ANNOUNCEMENT_WRITE')")
    fun revoke(@PathVariable id: String) = announcements.revoke(id)
}

@RestController
@RequestMapping("/api/inbox")
class SystemInboxController(private val inbox: SystemInbox) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
    ): PageResult<InboxEntry> = inbox.list(PageWindow(page, size), unreadOnly)

    @PutMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markRead(@PathVariable id: String) = inbox.markRead(id)
}
