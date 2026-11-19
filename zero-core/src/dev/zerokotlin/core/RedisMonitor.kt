package dev.zerokotlin.core

import io.swagger.v3.oas.annotations.media.Schema

/** 读取应用所用 Redis 缓存后端的运行状态及允许公开的统计。 */
fun interface RedisMonitor {
    /** 返回当前状态和可用的 Redis 指标。 */
    fun snapshot(): RedisSnapshot
}

/** 应用内置缓存对 Redis 的使用状态。 */
enum class RedisMonitorStatus {
    /** 当前缓存后端未使用 Redis。 */
    DISABLED,

    /** Redis 返回了 INFO 数据。 */
    AVAILABLE,

    /** 已选用 Redis，但暂时无法采集 INFO 数据。 */
    UNAVAILABLE,
}

/** Redis INFO 中允许通过运行监控接口返回的字段。 */
@Schema(description = "内置 Redis 缓存的状态及白名单指标。")
data class RedisSnapshot(
    @field:Schema(description = "DISABLED 表示未使用，AVAILABLE 表示可读，UNAVAILABLE 表示暂时无法读取。")
    val status: RedisMonitorStatus,
    @field:Schema(description = "Redis 版本号。")
    val version: String? = null,
    @field:Schema(description = "已连接客户端数。")
    val connectedClients: Long? = null,
    @field:Schema(description = "Redis 已分配的内存字节数。")
    val usedMemoryBytes: Long? = null,
    @field:Schema(description = "Redis 运行时长，单位为秒。")
    val uptimeSeconds: Long? = null,
)
