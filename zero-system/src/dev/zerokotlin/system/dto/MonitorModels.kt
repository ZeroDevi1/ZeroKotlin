package dev.zerokotlin.system.dto

import dev.zerokotlin.core.RedisSnapshot
import io.swagger.v3.oas.annotations.media.Schema

/** 当前应用所在环境的一次运行监控采样。 */
@Schema(description = "运行监控快照，探针无法提供的指标为 null。")
data class MonitorSnapshot(
    @field:Schema(description = "采样完成时间，单位为 Unix 毫秒。")
    val sampledAt: Long,
    val cpu: CpuSnapshot,
    @field:Schema(description = "JVM 可见的系统内存，系统未提供该指标时为 null。")
    val memory: SystemMemorySnapshot?,
    val jvm: JvmSnapshot,
    @field:Schema(description = "应用工作目录所在文件系统的容量，无法读取时为 null。")
    val disk: DiskSnapshot?,
    val redis: RedisSnapshot,
)

/** JVM 可见的处理器数量和近期负载。 */
@Schema(description = "CPU 指标。负载使用 0 到 1 的比例。")
data class CpuSnapshot(
    @field:Schema(description = "应用可用的逻辑处理器数。")
    val processors: Int,
    @field:Schema(description = "系统近期 CPU 使用比例。")
    val systemLoad: Double?,
    @field:Schema(description = "应用进程近期 CPU 使用比例。")
    val processLoad: Double?,
)

/** 操作系统或容器向当前 JVM 报告的内存。 */
@Schema(description = "JVM 可见的系统内存，单位为字节。")
data class SystemMemorySnapshot(
    @field:Schema(description = "总内存字节数。")
    val totalBytes: Long,
    @field:Schema(description = "空闲内存字节数。")
    val freeBytes: Long,
)

/** Java 运行时的版本、堆及运行时长。 */
@Schema(description = "Java 运行时版本和堆用量。")
data class JvmSnapshot(
    @field:Schema(description = "Java 运行时版本。")
    val version: String,
    @field:Schema(description = "已用堆字节数。")
    val heapUsedBytes: Long,
    @field:Schema(description = "已分配堆字节数。")
    val heapCommittedBytes: Long,
    @field:Schema(description = "最大堆字节数。")
    val heapMaxBytes: Long,
    @field:Schema(description = "Java 运行时已启动的毫秒数。")
    val uptimeMs: Long?,
)

/** 应用所在文件系统的容量。 */
@Schema(description = "应用工作目录所在文件系统的容量，单位为字节。")
data class DiskSnapshot(
    @field:Schema(description = "文件系统总容量。")
    val totalBytes: Long,
    @field:Schema(description = "当前应用可使用的空间。")
    val usableBytes: Long,
)
