package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

@TableName("sys_role")
class RoleRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    var code: String = "",
    var name: String = "",
    var dataScope: String = DataScope.SELF.name,
    var enabled: Int = 1,
)
