package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.FieldStrategy
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

/**
 * `sys_menu` 中的导航节点及其可见条件。
 *
 * @property id 菜单主键。
 * @property parentId 上级菜单 ID，`null` 表示顶级菜单。
 * @property name 菜单项的显示名称。
 * @property path 站内导航路径，未配置跳转时为 `null`。
 * @property permissionCode 菜单可见性所需的权限编码，`null` 表示没有权限点限制。
 * @property sortOrder 同级菜单的排序值，较小值排在前面。
 * @property enabled 启用标志，`1` 表示启用，`0` 表示停用。
 * @property moduleName 菜单所属的可选模块名。配置后，只有安装该模块时才进入用户菜单树。
 */
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
