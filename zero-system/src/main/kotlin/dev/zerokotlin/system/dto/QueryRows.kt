package dev.zerokotlin.system.dto

class UserRow(
    var id: String = "",
    var username: String = "",
    var displayName: String = "",
    var departmentId: String? = null,
    var enabled: Int = 1,
)

class AssignedRoleRow(
    var userId: String = "",
    var roleId: String = "",
    var code: String = "",
    var name: String = "",
)

class AuthorityRow(
    var code: String = "",
    var dataScope: String = "",
)
