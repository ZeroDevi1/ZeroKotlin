package dev.zerokotlin.ktor.system.realtime

import dev.zerokotlin.ktor.exists
import dev.zerokotlin.ktor.system.account.userVisibility
import dev.zerokotlin.ktor.system.auth.CurrentUser
import java.sql.Connection

/** 私发目标必须启用且落在发送者对该操作可见的用户范围内。 */
internal fun Connection.canReceiveMessage(id: String, sender: CurrentUser, authority: String): Boolean {
    val visibility = sender.userVisibility(authority)
    return exists(this, "SELECT 1 FROM sys_user u WHERE u.id = ? AND u.enabled = 1 AND (${visibility.condition})", id,
        *visibility.parameters.toTypedArray())
}
