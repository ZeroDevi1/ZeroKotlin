package dev.zerokotlin.core

import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import org.lionsoul.ip2region.xdb.Searcher
import org.lionsoul.ip2region.xdb.Version
import org.slf4j.LoggerFactory

/** 从配置的本地 xdb 文件查询 IP 归属地。IPv4 和 IPv6 独立装载。 */
class IpRegionLookup internal constructor(ipv4Path: String, ipv6Path: String) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val ipv4 = load(ipv4Path, Version.IPv4)
    private val ipv6 = load(ipv6Path, Version.IPv6)

    /**
     * 查询 IP 字面量。没有匹配数据、文件不可用或地址无效时返回 null。
     *
     * @param address 服务端接收的地址，可包含 IPv6 作用域后缀。
     * @return xdb 中的地域描述，省略用于填充的空字段。
     */
    fun find(address: String): String? {
        if (ipv4 == null && ipv6 == null) return null
        return try {
            val ip = InetAddress.ofLiteral(address.substringBefore('%')).address
            val searcher = (if (ip.size == 4) ipv4 else ipv6) ?: return null
            // 查询会修改文件指针和 I/O 计数，同一个 Searcher 串行使用。
            synchronized(searcher) { searcher.search(ip) }
                .split('|').map(String::trim).filter { it.isNotEmpty() && it != "0" }
                .joinToString(" / ").take(255).trimEnd { it.isHighSurrogate() }.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    /** 在应用关闭时释放两个数据库的文件句柄。 */
    override fun close() {
        listOfNotNull(ipv4, ipv6).forEach { searcher ->
            runCatching { synchronized(searcher) { searcher.close() } }
        }
    }

    /** 验证结构版本和地址族，再缓存向量索引。配置了无效文件时只停用对应地址族。 */
    private fun load(path: String, version: Version): Searcher? {
        if (path.isBlank()) return null
        return try {
            val file = File(path)
            require(file.isFile)
            val index = RandomAccessFile(file, "r").use { handle ->
                val header = Searcher.loadHeader(handle)
                val length = handle.length()
                Searcher.verify(header, length)
                require(Version.fromHeader(header) == version)
                require(header.startIndexPtr <= header.endIndexPtr && header.endIndexPtr + version.segmentIndexSize <= length)
                Searcher.loadVectorIndex(handle)
            }
            Searcher.newWithVectorIndex(version, file, index)
        } catch (_: Exception) {
            logger.warn("{} 归属地数据库不可用，请检查本地 xdb 文件配置", version.name)
            null
        }
    }
}
