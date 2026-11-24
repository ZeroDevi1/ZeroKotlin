package dev.zerokotlin.ktor.system.position

import dev.zerokotlin.ktor.Created
import dev.zerokotlin.ktor.Page
import dev.zerokotlin.ktor.Window
import kotlinx.serialization.Serializable

@Serializable internal data class Position(val id: String, val code: String, val name: String, val enabled: Boolean, val sortOrder: Int)
@Serializable internal data class NewPosition(val code: String, val name: String, val sortOrder: Int)
@Serializable internal data class EditPosition(val name: String, val enabled: Boolean, val sortOrder: Int)
@Serializable internal data class PositionMember(val id: String, val username: String, val displayName: String)
@Serializable internal data class PositionMembers(val userIds: Set<String?>)

/** 岗位列表按顺序读取，成员使用完整集合替换。 */
internal interface PositionService {
    suspend fun list(token: String, window: Window): Page<Position>
    suspend fun create(token: String, request: NewPosition): Created
    suspend fun update(token: String, id: String, request: EditPosition)
    suspend fun members(token: String, id: String): List<PositionMember>
    suspend fun replaceMembers(token: String, id: String, ids: Set<String?>)
}
