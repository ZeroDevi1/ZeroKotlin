package dev.zerokotlin.ktor.system.audit

import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.ktor.bearer
import io.ktor.server.application.log
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** 运行监控入口。状态采样与请求审计使用各自的事务边界。 */
internal fun Route.monitorController(service: MonitorService) {
    get("monitor") {
        val started = System.nanoTime()
        var actorId: String? = null
        var status = 200
        var exception: String? = null
        try {
            val actor = service.current(call.bearer())
            actorId = actor.id
            call.respond(service.snapshot(actor))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            status = (failure as? SystemFailure)?.status ?: 500
            exception = failure.javaClass.simpleName
            throw failure
        } finally {
            try {
                service.record(
                    actorId, status, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), exception,
                    call.request.origin.remoteHost,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                call.application.log.warn("无法记录运行监控审计（{}）", failure.javaClass.simpleName)
            }
        }
    }
}
