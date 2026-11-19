package dev.zerokotlin.system.dto

data class LoginAuditEntry(
    val id: String,
    val userId: String?,
    val username: String?,
    val successful: Boolean,
    val remoteAddress: String,
    val occurredAt: Long,
)

data class OperationAuditEntry(
    val id: String,
    val actorId: String,
    val action: String,
    val targetId: String,
    val occurredAt: Long,
)
