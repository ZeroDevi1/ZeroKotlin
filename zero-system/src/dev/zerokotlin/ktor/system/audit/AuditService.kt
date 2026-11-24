package dev.zerokotlin.ktor.system.audit

import dev.zerokotlin.ktor.Page
import dev.zerokotlin.ktor.SystemStore
import dev.zerokotlin.ktor.Window

/** 三类审计共用操作权限，保留各自的筛选及响应模型。 */
internal class AuditService(private val store: SystemStore) {
    private val authority = "system:audit:read"

    suspend fun logins(token: String, window: Window, successful: Boolean?): Page<LoginAudit> =
        store.read(token, authority) { db, _ -> db.loginAuditPage(window, successful) }

    suspend fun operations(token: String, window: Window, actorId: String?, action: String?): Page<OperationAudit> =
        store.read(token, authority) { db, _ -> db.operationAuditPage(window, actorId, action) }

    suspend fun requests(token: String, window: Window, successful: Boolean?): Page<RequestAudit> =
        store.read(token, authority) { db, _ -> db.requestAuditPage(window, successful) }
}
