package dev.zerokotlin.ktor.system.account

import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.Page
import dev.zerokotlin.ktor.Window
import dev.zerokotlin.ktor.system.auth.CurrentUser
import kotlinx.serialization.Serializable

internal const val userManage = "system:user:manage"

@Serializable
internal data class UserDetail(val user: User, val roleIds: List<String>)

@Serializable
internal data class CreateUser(
    val username: String,
    val displayName: String,
    val password: String,
    val departmentId: String? = null,
    val roleIds: Set<String> = emptySet(),
)

@Serializable
internal data class EditUserProfile(val displayName: String)

@Serializable
internal data class EditUserAccount(
    val departmentId: String? = null,
    val enabled: Boolean,
    val roleIds: Set<String>,
    val password: String? = null,
)

@Serializable
internal data class ResetUserPassword(val newPassword: String)

/** 账号读写受各操作的数据范围约束。安全状态变更需要同步维护会话。 */
internal interface AccountService {
    suspend fun list(token: String, window: Window, department: String?, role: String?): Page<UserEntry>
    suspend fun detail(token: String, id: String): UserDetail
    suspend fun create(token: String, request: CreateUser): Created
    suspend fun editProfile(token: String, id: String, request: EditUserProfile): User
    suspend fun editAccount(token: String, id: String, request: EditUserAccount)
    suspend fun resetPassword(token: String, id: String, password: String)
    suspend fun delete(token: String, ids: List<String?>)
}

internal fun requireAdministrator(actor: CurrentUser) {
    if (!actor.administrator) throw SystemFailure(403, "仅系统管理员可执行此操作")
}
