package dev.zerokotlin.core

/** 按权限点记录当前用户的业务操作，与调用方的业务写入共用事务。 */
fun interface OperationAudit {
    fun record(permission: String, targetId: String)
}
