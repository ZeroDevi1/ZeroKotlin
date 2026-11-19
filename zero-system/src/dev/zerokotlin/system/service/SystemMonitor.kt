package dev.zerokotlin.system.service

import com.sun.management.OperatingSystemMXBean
import dev.zerokotlin.core.RedisMonitor
import dev.zerokotlin.system.dto.CpuSnapshot
import dev.zerokotlin.system.dto.DiskSnapshot
import dev.zerokotlin.system.dto.JvmSnapshot
import dev.zerokotlin.system.dto.MonitorSnapshot
import dev.zerokotlin.system.dto.SystemMemorySnapshot
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import org.springframework.stereotype.Service

/** 采集应用的 CPU、内存、Java 运行时、磁盘和 Redis 指标。 */
@Service
class SystemMonitor(private val redis: RedisMonitor) {
    /** 即时采样。运行环境缺少可选指标时保留其余可用数据。 */
    fun snapshot(): MonitorSnapshot {
        val runtime = Runtime.getRuntime()
        val os = runCatching { ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean }.getOrNull()
        val cpu = CpuSnapshot(
            runtime.availableProcessors(),
            runCatching { os?.cpuLoad?.takeIf { it in 0.0..1.0 } }.getOrNull(),
            runCatching { os?.processCpuLoad?.takeIf { it in 0.0..1.0 } }.getOrNull(),
        )
        val memory = runCatching {
            os?.let {
                val total = it.totalMemorySize
                val free = it.freeMemorySize
                if (total > 0 && free in 0L..total) SystemMemorySnapshot(total, free) else null
            }
        }.getOrNull()
        val heapCommitted = runtime.totalMemory()
        val jvm = JvmSnapshot(
            Runtime.version().toString(),
            (heapCommitted - runtime.freeMemory()).coerceAtLeast(0),
            heapCommitted,
            runtime.maxMemory(),
            runCatching { ManagementFactory.getRuntimeMXBean().uptime }.getOrNull(),
        )
        val disk = runCatching {
            val store = Files.getFileStore(Path.of(".").toAbsolutePath())
            DiskSnapshot(store.totalSpace, store.usableSpace)
        }.getOrNull()
        val redisSnapshot = redis.snapshot()
        return MonitorSnapshot(System.currentTimeMillis(), cpu, memory, jvm, disk, redisSnapshot)
    }
}
