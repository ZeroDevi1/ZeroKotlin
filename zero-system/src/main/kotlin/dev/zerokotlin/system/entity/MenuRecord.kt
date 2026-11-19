package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.FieldStrategy
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

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
