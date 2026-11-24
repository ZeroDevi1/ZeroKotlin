package dev.zerokotlin.ktor.system.role

import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.Page
import dev.zerokotlin.ktor.Window
import kotlinx.serialization.Serializable

@Serializable internal data class Role(val id: String, val code: String, val name: String, val dataScope: String, val enabled: Boolean)
@Serializable internal data class RoleDetail(val role: Role, val permissionCodes: List<String>)
@Serializable internal data class CreateRole(
    val code: String, val name: String, val dataScope: String, val permissionCodes: Set<String> = emptySet(),
)
@Serializable internal data class UpdateRole(val name: String, val dataScope: String, val enabled: Boolean, val permissionCodes: Set<String>)
@Serializable internal enum class MenuMode { LEGACY, DIRECT }
@Serializable internal data class RoleMenus(val mode: MenuMode, val menuIds: Set<String>)

/** 管理业务角色及其权限和菜单。内置管理员角色的数据由系统维护。 */
internal interface RoleService {
    suspend fun list(token: String, window: Window, enabled: Boolean?): Page<Role>
    suspend fun create(token: String, request: CreateRole): Created
    suspend fun detail(token: String, id: String): RoleDetail
    suspend fun update(token: String, id: String, request: UpdateRole)
    suspend fun delete(token: String, ids: List<String?>)
    suspend fun menus(token: String, id: String): RoleMenus
    suspend fun updateMenus(token: String, id: String, request: RoleMenus)
}
