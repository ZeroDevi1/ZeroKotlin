package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

/**
 * `sys_operation_audit` 中一条系统或模块操作的审计记录。
 *
 * @property id 操作审计主键。
 * @property actorId 执行操作的用户 ID。
 * @property action 审计动作标识或记录该操作的权限编码。
 * @property targetId 被操作对象的标识，如用户 ID 或权限编码。
 * @property occurredAt 操作记录时间，单位为 Unix 毫秒。
 */
@TableName("sys_operation_audit")
class OperationAuditRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    var actorId: String = "",
    var action: String = "",
    var targetId: String = "",
    var occurredAt: Long = 0,
)
