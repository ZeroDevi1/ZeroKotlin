package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

/**
 * `sys_session` 中用于验证访问令牌的登录会话。
 *
 * @property digest 会话主键，为访问令牌 SHA-256 摘要的小写十六进制字符串。
 * @property userId 会话所属的用户 ID。
 * @property expiresAt 会话到期时间，单位为 Unix 毫秒。到达该时刻即失效。
 */
@TableName("sys_session")
class SessionRecord(
    @field:TableId(type = IdType.INPUT)
    var digest: String = "",
    var userId: String = "",
    var expiresAt: Long = 0,
)
