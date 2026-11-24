package dev.zerokotlin.ktor.system.permission.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

/** 原 Flyway 表使用权限编码作主键，启用标记跨三个数据库统一存储为 0 或 1。 */
@TableName("sys_permission")
internal class PermissionRecord {
    @field:TableId(type = IdType.INPUT)
    var code: String? = null
    var name: String? = null
    var enabled: Int? = null
}
