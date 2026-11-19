package dev.zerokotlin.system.dto

class CreateUserRequest(
    val username: String,
    val displayName: String,
    val password: String,
    val departmentId: String? = null,
    val roleIds: Set<String> = emptySet(),
)

class UserAccountRequest(
    val departmentId: String? = null,
    val enabled: Boolean?,
    val roleIds: Set<String>,
    val password: String? = null,
)

data class UserProfileRequest(val displayName: String)

data class UserSummary(
    val id: String,
    val username: String,
    val displayName: String,
    val departmentId: String?,
    val enabled: Boolean,
)

data class UserDetails(val user: UserSummary, val roleIds: List<String>)

data class AssignedRole(val id: String, val code: String, val name: String)

data class UserListEntry(val user: UserSummary, val roles: List<AssignedRole>)
