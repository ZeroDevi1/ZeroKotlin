package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

/**
 * `sys_permission` 中的权限点定义，可被角色或菜单引用。
 *
 * @property code 权限点主键，用于授权关联和权限校验。
 * @property name 权限点的显示名称。
 * @property enabled 启用标志，`1` 表示参与有效授权计算，`0` 表示停用。
 */
@TableName("sys_permission")
class PermissionRecord(
    @field:TableId(type = IdType.INPUT)
    var code: String = "",
    var name: String = "",
    var enabled: Int = 1,
)
