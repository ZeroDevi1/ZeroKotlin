package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.FieldStrategy
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

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
