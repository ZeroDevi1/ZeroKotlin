package dev.zerokotlin.system.dto

data class DepartmentRequest(val name: String, val parentId: String? = null, val sortOrder: Int = 0)

data class DepartmentNode(val id: String, val name: String, val sortOrder: Int, val children: List<DepartmentNode>)

data class MenuRequest(
    val name: String,
    val parentId: String? = null,
    val path: String? = null,
    val permissionCode: String? = null,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
)

data class MenuNode(
    val id: String,
    val name: String,
    val path: String?,
    val permissionCode: String?,
    val sortOrder: Int,
    val enabled: Boolean,
    val children: List<MenuNode>,
)
