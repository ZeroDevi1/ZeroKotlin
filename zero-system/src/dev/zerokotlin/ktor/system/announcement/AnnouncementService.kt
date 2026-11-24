package dev.zerokotlin.ktor.system.announcement

import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.Page
import dev.zerokotlin.ktor.Window
import kotlinx.serialization.Serializable

@Serializable internal data class EditAnnouncement(val title: String, val body: String)

@Serializable internal data class Announcement(
    val id: String, val title: String, val body: String, val status: String,
    val createdAt: Long, val updatedAt: Long, val publishedAt: Long?,
)

/** 草稿可编辑和发布，已发布公告可撤回。 */
internal interface AnnouncementService {
    suspend fun list(token: String, window: Window): Page<Announcement>
    suspend fun create(token: String, request: EditAnnouncement): Created
    suspend fun update(token: String, id: String, request: EditAnnouncement)
    suspend fun publish(token: String, id: String)
    suspend fun revoke(token: String, id: String)
}
