package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.FieldStrategy
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

@TableName("sys_department")
class DepartmentRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    @field:TableField(updateStrategy = FieldStrategy.ALWAYS)
    var parentId: String? = null,
    var name: String = "",
    var sortOrder: Int = 0,
)
