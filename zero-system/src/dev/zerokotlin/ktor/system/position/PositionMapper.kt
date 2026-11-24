package dev.zerokotlin.ktor.system.position

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.ktor.change
import dev.zerokotlin.ktor.invalid
import dev.zerokotlin.ktor.rows
import java.sql.Connection

/** 岗位记录使用 BaseMapper，成员投影保留跨表查询。 */
@TableName("sys_position")
internal class PositionRecord {
    @field:TableId(type = IdType.INPUT)
    var id: String? = null
    var code: String? = null
    var name: String? = null
    var enabled: Int? = null
    @field:TableField("sort_order")
    var sortOrder: Int? = null

    fun summary() = Position(checkNotNull(id), checkNotNull(code), checkNotNull(name), enabled == 1, checkNotNull(sortOrder))
}

internal interface PositionMapper : BaseMapper<PositionRecord>

internal fun Connection.positionMembers(id: String): List<PositionMember> =
    rows(
        """SELECT u.id, u.username, u.display_name FROM sys_user_position p
           JOIN sys_user u ON u.id = p.user_id WHERE p.position_id = ? ORDER BY u.username, u.id""", id,
    ) { PositionMember(it.getString(1), it.getString(2), it.getString(3)) }

/** 分批绑定成员 ID，在任何删除前确认所有成员存在。 */
internal fun Connection.replacePositionMembers(id: String, ids: List<String>) {
    for (batch in ids.chunked(500)) {
        val marks = batch.joinToString(",") { "?" }
        val count = rows("SELECT COUNT(*) FROM sys_user WHERE id IN ($marks)", *batch.toTypedArray()) {
            it.getInt(1)
        }.single()
        if (count != batch.size) invalid("用户不存在")
    }
    change("DELETE FROM sys_user_position WHERE position_id = ?", id)
    ids.forEach { change("INSERT INTO sys_user_position (position_id, user_id) VALUES (?, ?)", id, it) }
}
