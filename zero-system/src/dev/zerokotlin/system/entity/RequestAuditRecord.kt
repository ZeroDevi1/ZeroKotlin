package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

/** 保存在 `sys_request_audit` 的 HTTP 请求元数据及结果。 */
@TableName("sys_request_audit")
class RequestAuditRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    var actorId: String? = null,
    var action: String = "",
    var httpMethod: String = "",
    var routePattern: String = "",
    var httpStatus: Int = 0,
    var durationMs: Long = 0,
    var exceptionType: String? = null,
    var remoteAddress: String = "",
    var ipRegion: String? = null,
    var userAgent: String? = null,
    var occurredAt: Long = 0,
)
