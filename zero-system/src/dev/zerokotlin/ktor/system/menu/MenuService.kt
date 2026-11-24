package dev.zerokotlin.ktor.system.menu

import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.system.auth.MenuNode
import kotlinx.serialization.Serializable

@Serializable
internal data class MenuRequest(
    val name: String, val parentId: String? = null, val path: String? = null, val permissionCode: String? = null,
    val sortOrder: Int = 0, val enabled: Boolean = true,
)

/** 管理视图包含停用节点。写入校验路径、权限点和父子关系。 */
internal interface MenuService {
    suspend fun tree(token: String): List<MenuNode>
    suspend fun create(token: String, request: MenuRequest): Created
    suspend fun update(token: String, id: String, request: MenuRequest)
}
