package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

@TableName("sys_permission")
class PermissionRecord(
    @field:TableId(type = IdType.INPUT)
    var code: String = "",
    var name: String = "",
    var enabled: Int = 1,
)
