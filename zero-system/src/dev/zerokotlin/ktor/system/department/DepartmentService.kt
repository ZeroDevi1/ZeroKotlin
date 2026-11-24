package dev.zerokotlin.ktor.system.department

import dev.zerokotlin.ktor.Created
import kotlinx.serialization.Serializable

@Serializable internal data class DepartmentRequest(val name: String, val parentId: String? = null, val sortOrder: Int = 0)
@Serializable internal data class DepartmentNode(val id: String, val name: String, val sortOrder: Int, val children: List<DepartmentNode>)

/** 部门树按排序和 ID 读取。创建和移动保持父子关系无环。 */
internal interface DepartmentService {
    suspend fun tree(token: String): List<DepartmentNode>
    suspend fun create(token: String, request: DepartmentRequest): Created
    suspend fun update(token: String, id: String, request: DepartmentRequest)
}
