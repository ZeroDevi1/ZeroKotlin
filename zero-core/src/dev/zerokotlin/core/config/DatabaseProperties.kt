package dev.zerokotlin.core.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * `zero.database` 下的数据源选择和连接配置。
 *
 * @property mode 数据源选择模式，默认为 [DatabaseMode.AUTO]。
 * @property sqlite SQLite 文件配置。
 * @property postgresql PostgreSQL JDBC 配置。
 * @property mysql MySQL JDBC 配置。
 * @property probeTimeout 自动模式中每个解析出的 IP 地址的 TCP 连接超时，默认 1 秒。
 *   转为整数毫秒后须在 1 至 [Int.MAX_VALUE] 之间。DNS 查询和 JDBC 登录各自处理超时。
 */
@ConfigurationProperties("zero.database")
internal class DatabaseProperties(
    val mode: DatabaseMode = DatabaseMode.AUTO,
    val sqlite: SqliteProperties = SqliteProperties(),
    val postgresql: RemoteDatabaseProperties = RemoteDatabaseProperties(),
    val mysql: RemoteDatabaseProperties = RemoteDatabaseProperties(),
    val probeTimeout: Duration = Duration.ofSeconds(1),
)

/** 应用启动时选择数据源的方式。 */
internal enum class DatabaseMode {
    /** 直接打开配置的 SQLite 文件。 */
    SQLITE,

    /** 直接连接配置的 PostgreSQL，要求提供 JDBC URL。 */
    POSTGRESQL,

    /** 直接连接配置的 MySQL，要求提供 JDBC URL。 */
    MYSQL,

    /** 依次探测已配置的 PostgreSQL 和 MySQL 的 TCP 端口，都不可用时使用 SQLite。 */
    AUTO
}

/**
 * SQLite 文件配置。
 *
 * @property path 数据库文件路径，默认 `data/zero-kotlin.db`。相对路径从进程工作目录解析。
 */
internal class SqliteProperties(val path: String = "data/zero-kotlin.db")

/**
 * PostgreSQL 和 MySQL 共用的 JDBC 连接配置。
 *
 * @property url 默认未配置。格式为 `jdbc:<驱动>://host[:port]/database`，可带 JDBC 查询参数。
 *   PostgreSQL 和 MySQL 的默认端口分别为 5432 和 3306。URL 缺失时自动模式跳过该候选，显式远端模式启动失败。
 * @property username 传给 JDBC 连接池的用户名，默认未配置。
 * @property password 传给 JDBC 连接池的密码，默认未配置。
 */
internal class RemoteDatabaseProperties(
    val url: String? = null,
    val username: String? = null,
    val password: String? = null,
)
