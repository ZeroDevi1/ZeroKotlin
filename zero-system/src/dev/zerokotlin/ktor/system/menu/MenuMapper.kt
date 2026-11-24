package dev.zerokotlin.ktor.system.menu

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.baomidou.mybatisplus.core.mapper.BaseMapper

/** 管理端菜单持久化映射，保留 Flyway 中的列和整数启用标记。 */
@TableName("sys_menu")
internal class MenuRecord {
    @field:TableId(type = IdType.INPUT)
    var id: String? = null
    var name: String? = null
    @field:TableField("parent_id")
    var parentId: String? = null
    var path: String? = null
    @field:TableField("permission_code")
    var permissionCode: String? = null
    @field:TableField("sort_order")
    var sortOrder: Int? = null
    var enabled: Int? = null
}

internal interface MenuMapper : BaseMapper<MenuRecord>
