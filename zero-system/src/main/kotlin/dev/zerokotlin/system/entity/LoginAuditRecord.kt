package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

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
