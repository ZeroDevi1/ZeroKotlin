package dev.zerokotlin.ktor.system.announcement

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.plugins.pagination.Page as MybatisPage
import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.Page
import dev.zerokotlin.ktor.SystemStore
import dev.zerokotlin.ktor.Window
import dev.zerokotlin.ktor.invalid
import dev.zerokotlin.ktor.missing
import dev.zerokotlin.ktor.newId
import java.sql.Connection

/** 每次状态更新在持有系统写锁时复核原状态，并提交该操作的审计。 */
internal class AnnouncementServiceImpl(private val store: SystemStore) : AnnouncementService {
    private val write = "system:announcement:write"

    override suspend fun list(token: String, window: Window): Page<Announcement> =
        store.read(token, "system:announcement:read") { db, _ ->
            mapper(db) { announcements ->
                val query = QueryWrapper<AnnouncementRecord>().orderByDesc("created_at", "id")
                val page = announcements.selectPage(MybatisPage<AnnouncementRecord>(window.page.toLong(), window.size.toLong()), query)
                window.result(page.records.map(AnnouncementRecord::summary), page.total)
            }
        }

    override suspend fun create(token: String, request: EditAnnouncement): Created {
        val id = newId()
        store.write(token, write) { db, _, audit ->
            val now = System.currentTimeMillis()
            mapper(db) { announcements ->
                check(announcements.insert(AnnouncementRecord().also {
                    it.id = id
                    it.title = store.plainName(request.title)
                    it.body = body(request.body)
                    it.status = "DRAFT"
                    it.createdAt = now
                    it.updatedAt = now
                }) == 1)
            }
            audit("announcement.create", id)
        }
        return Created(id)
    }

    override suspend fun update(token: String, id: String, request: EditAnnouncement) {
        store.write(token, write) { db, _, audit ->
            mapper(db) { announcements ->
                val stored = announcements.selectById(id) ?: missing("公告")
                if (stored.status != "DRAFT") throw SystemFailure(409, "仅草稿公告可以修改")
                announcements.updateById(AnnouncementRecord().also {
                    it.id = id
                    it.title = store.plainName(request.title)
                    it.body = body(request.body)
                    it.updatedAt = System.currentTimeMillis()
                })
            }
            audit("announcement.update", id)
        }
    }

    override suspend fun publish(token: String, id: String) = transition(token, id, publish = true)

    override suspend fun revoke(token: String, id: String) = transition(token, id, publish = false)

    private suspend fun transition(token: String, id: String, publish: Boolean) {
        store.write(token, write) { db, _, audit ->
            mapper(db) { announcements ->
                val stored = announcements.selectById(id) ?: missing("公告")
                val expected = if (publish) "DRAFT" else "PUBLISHED"
                if (stored.status != expected) throw SystemFailure(409, "当前公告状态不允许此操作")
                val now = System.currentTimeMillis()
                announcements.updateById(AnnouncementRecord().also {
                    it.id = id
                    it.status = if (publish) "PUBLISHED" else "REVOKED"
                    it.updatedAt = now
                    if (publish) it.publishedAt = now
                })
            }
            audit(if (publish) "announcement.publish" else "announcement.revoke", id)
        }
    }

    private fun body(value: String) =
        value.trim().takeIf { it.length in 1..16000 } ?: invalid("正文长度须为 1 至 16000 字符")

    private fun <T> mapper(db: Connection, action: (AnnouncementMapper) -> T): T =
        store.mappers.withSession(db) { action(it.getMapper(AnnouncementMapper::class.java)) }
}
