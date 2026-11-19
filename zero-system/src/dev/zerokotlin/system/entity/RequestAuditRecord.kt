package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

/**
 * 保存在 `sys_request_audit` 的 HTTP 请求元数据及结果。
 *
 * @property id 审计记录的主键。
 * @property actorId 已认证用户 ID，请求没有认证主体时为空。
 * @property action 请求匹配的审计动作标识。
 * @property httpMethod HTTP 请求方法。
 * @property routePattern 匹配的路由模板。
 * @property httpStatus 请求结束时的 HTTP 响应状态码。
 * @property durationMs 请求处理耗时，单位为毫秒。
 * @property exceptionType 请求处理异常的类型，无异常时为空。
 * @property remoteAddress 采集的请求来源 IP 地址。
 * @property ipRegion 根据来源 IP 查询到的可选地域描述。
 * @property userAgent 请求头中的可选 User-Agent。
 * @property occurredAt 审计事件的 Unix 毫秒时间戳。
 */
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
