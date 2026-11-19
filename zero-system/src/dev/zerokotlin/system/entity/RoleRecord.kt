package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

/**
 * `sys_role` 中的角色定义，权限关联由独立关联表保存。
 *
 * @property id 角色主键。
 * @property code 角色的唯一业务编码。
 * @property name 角色的显示名称。
 * @property dataScope 角色授予权限时适用的 [DataScope] 枚举名，默认 `SELF`。
 * @property enabled 启用标志，`1` 表示参与有效授权计算，`0` 表示停用。
 */
@TableName("sys_role")
class RoleRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    var code: String = "",
    var name: String = "",
    var dataScope: String = DataScope.SELF.name,
    var enabled: Int = 1,
)
