package dev.zerokotlin.system.dto

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
