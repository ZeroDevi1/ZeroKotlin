package dev.zerokotlin.ktor

import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.core.security.RedisSnapshot
import dev.zerokotlin.core.security.SecurityState
import com.sun.management.OperatingSystemMXBean
import io.ktor.server.application.log
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Serializable private data class Cpu(val processors: Int, val systemLoad: Double?, val processLoad: Double?)
@Serializable private data class Memory(val totalBytes: Long, val freeBytes: Long)
@Serializable private data class Heap(
    val version: String, val heapUsedBytes: Long, val heapCommittedBytes: Long, val heapMaxBytes: Long, val uptimeMs: Long?,
)
@Serializable private data class Disk(val totalBytes: Long, val usableBytes: Long)
@Serializable private data class Snapshot(val sampledAt: Long, val cpu: Cpu, val memory: Memory?, val jvm: Heap, val disk: Disk?, val redis: RedisSnapshot)

internal fun Route.monitorRoutes(store: SystemStore, security: SecurityState) {
    get("monitor") {
        val started = System.nanoTime()
        var actorId: String? = null
        var status = 200
        var exception: String? = null
        try {
            val actor = store.current(call.bearer())
            actorId = actor.id
            if ("system:monitor:read" !in actor.permissions) throw SystemFailure(403, "没有权限")
            call.respond(withContext(Dispatchers.IO) { monitorSnapshot(security.redisSnapshot()) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            status = (failure as? SystemFailure)?.status ?: 500
            exception = failure.javaClass.simpleName
            throw failure
        } finally {
            try {
                store.monitorAudit(
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

private fun monitorSnapshot(redis: RedisSnapshot): Snapshot {
    val runtime = Runtime.getRuntime()
    val os = runCatching { ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean }.getOrNull()
    val cpu = Cpu(
        runtime.availableProcessors(),
        runCatching { os?.cpuLoad?.takeIf { it in 0.0..1.0 } }.getOrNull(),
        runCatching { os?.processCpuLoad?.takeIf { it in 0.0..1.0 } }.getOrNull(),
    )
    val memory = runCatching {
        os?.let {
            val total = it.totalMemorySize
            val free = it.freeMemorySize
            if (total > 0 && free in 0..total) Memory(total, free) else null
        }
    }.getOrNull()
    val committed = runtime.totalMemory()
    val heap = Heap(
        Runtime.version().toString(), (committed - runtime.freeMemory()).coerceAtLeast(0), committed, runtime.maxMemory(),
        runCatching { ManagementFactory.getRuntimeMXBean().uptime }.getOrNull(),
    )
    val disk = runCatching { Files.getFileStore(Path.of(".").toAbsolutePath()).let { Disk(it.totalSpace, it.usableSpace) } }.getOrNull()
    return Snapshot(System.currentTimeMillis(), cpu, memory, heap, disk, redis)
}
