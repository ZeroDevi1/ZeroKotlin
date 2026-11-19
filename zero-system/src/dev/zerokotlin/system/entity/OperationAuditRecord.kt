package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

@TableName("sys_operation_audit")
class OperationAuditRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    var actorId: String = "",
    var action: String = "",
    var targetId: String = "",
    var occurredAt: Long = 0,
)
