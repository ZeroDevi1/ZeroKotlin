package dev.zerokotlin.core

/** 按权限点记录当前用户的业务操作，与调用方的业务写入共用事务。 */
fun interface OperationAudit {
    /**
     * 校验当前用户权限，并在调用方的写事务内记录操作。
     *
     * @param permission 用于授权检查和写入审计记录的权限点。
     * @param targetId 被操作的业务对象 ID。
     */
    fun record(permission: String, targetId: String)
}
