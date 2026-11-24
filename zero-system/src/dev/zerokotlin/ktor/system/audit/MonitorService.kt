package dev.zerokotlin.ktor.system.audit

import com.sun.management.OperatingSystemMXBean
import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.core.security.RedisSnapshot
import dev.zerokotlin.core.security.SecurityState
import dev.zerokotlin.ktor.SystemStore
import dev.zerokotlin.ktor.system.auth.CurrentUser
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable internal data class Cpu(val processors: Int, val systemLoad: Double?, val processLoad: Double?)
@Serializable internal data class Memory(val totalBytes: Long, val freeBytes: Long)
@Serializable internal data class Heap(
    val version: String, val heapUsedBytes: Long, val heapCommittedBytes: Long, val heapMaxBytes: Long, val uptimeMs: Long?,
)
@Serializable internal data class Disk(val totalBytes: Long, val usableBytes: Long)
@Serializable internal data class Snapshot(val sampledAt: Long, val cpu: Cpu, val memory: Memory?, val jvm: Heap, val disk: Disk?, val redis: RedisSnapshot)

/** 监控采样不占用写锁，HTTP 状态和耗时由 Controller 报告。 */
internal class MonitorService(private val store: SystemStore, private val security: SecurityState) {
    suspend fun current(token: String): CurrentUser = store.current(token)

    suspend fun snapshot(actor: CurrentUser): Snapshot {
        if ("system:monitor:read" !in actor.permissions) throw SystemFailure(403, "没有权限")
        return withContext(Dispatchers.IO) { monitorSnapshot(security.redisSnapshot()) }
    }

    suspend fun record(actorId: String?, status: Int, duration: Long, failure: String?, address: String) {
        store.monitorAudit(actorId, status, duration, failure, address)
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
