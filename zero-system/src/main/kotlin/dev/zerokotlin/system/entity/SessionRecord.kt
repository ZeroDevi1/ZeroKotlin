package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

@TableName("sys_session")
class SessionRecord(
    @field:TableId(type = IdType.INPUT)
    var digest: String = "",
    var userId: String = "",
    var expiresAt: Long = 0,
)
