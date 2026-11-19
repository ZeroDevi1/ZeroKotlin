package dev.zerokotlin.system

import com.baomidou.mybatisplus.annotation.FieldStrategy
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

enum class DataScope {
    SELF,
    DEPARTMENT,
    DEPARTMENT_AND_DESCENDANTS,
    ALL,
}

@TableName("sys_user")
class UserRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    var username: String = "",
    var displayName: String = "",
    var passwordHash: String = "",
    @field:TableField(updateStrategy = FieldStrategy.ALWAYS)
    var departmentId: String? = null,
    var enabled: Int = 1,
)

@TableName("sys_department")
class DepartmentRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    @field:TableField(updateStrategy = FieldStrategy.ALWAYS)
    var parentId: String? = null,
    var name: String = "",
    var sortOrder: Int = 0,
)

@TableName("sys_role")
class RoleRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    var code: String = "",
    var name: String = "",
    var dataScope: String = DataScope.SELF.name,
    var enabled: Int = 1,
)

@TableName("sys_permission")
class PermissionRecord(
    @field:TableId(type = IdType.INPUT)
    var code: String = "",
    var name: String = "",
    var enabled: Int = 1,
)

@TableName("sys_menu")
class MenuRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    @field:TableField(updateStrategy = FieldStrategy.ALWAYS)
    var parentId: String? = null,
    var name: String = "",
    @field:TableField(updateStrategy = FieldStrategy.ALWAYS)
    var path: String? = null,
    @field:TableField(updateStrategy = FieldStrategy.ALWAYS)
    var permissionCode: String? = null,
    var sortOrder: Int = 0,
    var enabled: Int = 1,
    var moduleName: String? = null,
)

@TableName("sys_dictionary")
class DictionaryRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    var code: String = "",
    var name: String = "",
    var enabled: Int = 1,
)

@TableName("sys_dictionary_item")
class DictionaryItemRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    var dictionaryId: String = "",
    var itemValue: String = "",
    var label: String = "",
    var sortOrder: Int = 0,
    var enabled: Int = 1,
)

@TableName("sys_session")
class SessionRecord(
    @field:TableId(type = IdType.INPUT)
    var digest: String = "",
    var userId: String = "",
    var expiresAt: Long = 0,
)

@TableName("sys_login_audit")
class LoginAuditRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    var userId: String? = null,
    var username: String? = null,
    var successful: Int = 0,
    var remoteAddress: String = "",
    var occurredAt: Long = 0,
)

@TableName("sys_operation_audit")
class OperationAuditRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    var actorId: String = "",
    var action: String = "",
    var targetId: String = "",
    var occurredAt: Long = 0,
)

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
