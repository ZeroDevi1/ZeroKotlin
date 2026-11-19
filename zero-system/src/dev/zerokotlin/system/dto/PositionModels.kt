package dev.zerokotlin.system.dto

data class CreatePositionRequest(val code: String, val name: String, val sortOrder: Int?)

data class UpdatePositionRequest(val name: String, val enabled: Boolean?, val sortOrder: Int?)

data class ReplacePositionMembersRequest(val userIds: Set<String?>)

data class PositionEntry(val id: String, val code: String, val name: String, val enabled: Boolean, val sortOrder: Int)

data class PositionMember(val userId: String, val username: String, val displayName: String)
