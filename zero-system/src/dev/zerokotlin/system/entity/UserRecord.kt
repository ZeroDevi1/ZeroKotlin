package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.FieldStrategy
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

/**
 * `sys_user` 中的账号及认证所需信息。
 *
 * @property id 用户主键。
 * @property username 规范化为小写的唯一登录名。
 * @property displayName 界面展示的用户名称。
 * @property passwordHash 密码编码器生成的哈希，用于验证登录密码。
 * @property departmentId 所属部门 ID，更新为 `null` 可清除部门归属。
 * @property enabled 启用标志，`1` 表示启用，`0` 表示停用。
 * @property email 用户自行维护的可选邮箱。
 * @property mobile 用户自行维护的可选手机号。
 */
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
    var email: String? = null,
    var mobile: String? = null,
)
