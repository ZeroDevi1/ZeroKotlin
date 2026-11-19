package dev.zerokotlin.system.service

import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.LoginAuditEntry
import dev.zerokotlin.system.dto.OperationAuditEntry
import dev.zerokotlin.system.dto.RequestAuditEntry
import dev.zerokotlin.system.entity.LoginAuditRecord
import dev.zerokotlin.system.entity.OperationAuditRecord
import dev.zerokotlin.system.entity.RequestAuditRecord
import dev.zerokotlin.system.mapper.LoginAuditMapper
import dev.zerokotlin.system.mapper.OperationAuditMapper
import dev.zerokotlin.system.mapper.RequestAuditMapper
import dev.zerokotlin.system.support.flag
import dev.zerokotlin.system.support.query
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 在只读事务中查询登录、操作和请求审计，返回页数据及相同条件下的总数。
 *
 * @property logins 读取登录尝试及其结果。
 * @property operations 读取操作审计及涉及的操作者和资源。
 * @property requests 读取端点的请求审计。
 */
@Service
class SystemAudits(
    private val logins: LoginAuditMapper,
    private val operations: OperationAuditMapper,
    private val requests: RequestAuditMapper,
) {
    /**
     * 按登录结果筛选审计记录。
     *
     * @param window 页码和每页条数。
     * @param successful 为 true 时查成功尝试，为 false 时查失败尝试，为 null 时查询两者。
     * @return 按发生时间和 ID 倒序排列的登录审计页。
     */
    @Transactional(readOnly = true)
    fun loginPage(window: PageWindow, successful: Boolean?): PageResult<LoginAuditEntry> {
        val results = logins.selectPage(
            Page<LoginAuditRecord>(window.page.toLong(), window.size.toLong()),
            query<LoginAuditRecord>().eq(successful != null, "successful", successful?.flag())
                .orderByDesc("occurred_at", "id"),
        )

        val entries = results.records.map {
            LoginAuditEntry(it.id, it.userId, it.username, it.successful == 1, it.remoteAddress, it.occurredAt, it.ipRegion)
        }
        return window.result(entries, results.total)
    }

    /**
     * 按操作者和动作筛选操作记录。
     *
     * @param window 页码和每页条数。
     * @param actorId 要精确匹配的操作者用户 ID，null 表示不按操作者筛选。
     * @param action 要精确匹配的审计动作标识，null 表示不按动作筛选。
     * @return 按发生时间和 ID 倒序排列的操作审计页。
     */
    @Transactional(readOnly = true)
    fun operationPage(window: PageWindow, actorId: String?, action: String?): PageResult<OperationAuditEntry> {
        val results = operations.selectPage(
            Page<OperationAuditRecord>(window.page.toLong(), window.size.toLong()),
            query<OperationAuditRecord>().eq(actorId != null, "actor_id", actorId).eq(action != null, "action", action)
                .orderByDesc("occurred_at", "id"),
        )

        val entries = results.records.map {
            OperationAuditEntry(it.id, it.actorId, it.action, it.targetId, it.occurredAt)
        }
        return window.result(entries, results.total)
    }

    /**
     * 按 HTTP 结果分页查询请求审计。
     *
     * @param window 页码和每页条数。
     * @param successful 为 true 时查询低于 400 的状态，为 false 时查询 400 及以上，null 时查询全部。
     * @return 按发生时间和 ID 倒序排列的请求审计页。
     */
    @Transactional(readOnly = true)
    fun requestPage(window: PageWindow, successful: Boolean?): PageResult<RequestAuditEntry> {
        val results = requests.selectPage(
            Page<RequestAuditRecord>(window.page.toLong(), window.size.toLong()),
            query<RequestAuditRecord>()
                .lt(successful == true, "http_status", 400)
                .ge(successful == false, "http_status", 400)
                .orderByDesc("occurred_at", "id"),
        )
        val entries = results.records.map {
            RequestAuditEntry(
                it.id, it.actorId, it.action, it.httpMethod, it.routePattern, it.httpStatus, it.durationMs,
                it.exceptionType, it.remoteAddress, it.ipRegion, it.userAgent, it.occurredAt,
            )
        }
        return window.result(entries, results.total)
    }
}
