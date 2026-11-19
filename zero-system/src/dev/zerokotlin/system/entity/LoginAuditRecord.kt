package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

/**
 * `sys_login_audit` 中一次登录尝试的结果。
 *
 * @property id 登录审计主键。
 * @property userId 登录名对应的用户 ID，无法匹配账号时为 `null`。
 * @property username 规范化后的登录名，输入格式无效时为 `null`。
 * @property successful 登录结果，`1` 表示成功建立会话，`0` 表示失败。
 * @property remoteAddress 服务端记录的请求对端地址。
 * @property occurredAt 登录尝试时间，单位为 Unix 毫秒。
 * @property ipRegion 可选的离线 IP 归属地。
 */
@TableName("sys_login_audit")
class LoginAuditRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    var userId: String? = null,
    var username: String? = null,
    var successful: Int = 0,
    var remoteAddress: String = "",
    var occurredAt: Long = 0,
    var ipRegion: String? = null,
)
