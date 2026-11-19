package dev.zerokotlin.system.dto

import dev.zerokotlin.system.entity.DataScope

class LoginRequest(val username: String, val password: String)

data class LoginResponse(val accessToken: String, val tokenType: String, val expiresAt: Long)

data class CurrentUserResponse(
    val id: String,
    val username: String,
    val displayName: String,
    val departmentId: String?,
    val permissions: Map<String, DataScope>,
    val administrator: Boolean,
)
