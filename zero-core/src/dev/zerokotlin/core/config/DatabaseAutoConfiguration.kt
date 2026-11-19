package dev.zerokotlin.core.config

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.zerokotlin.core.ModuleMigration
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URISyntaxException
import java.net.UnknownHostException
import java.nio.file.Files
import java.nio.file.Path
import org.apache.ibatis.session.SqlSessionFactory
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy
import org.springframework.boot.jdbc.DatabaseDriver
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.sql.init.dependency.AbstractBeansOfTypeDependsOnDatabaseInitializationDetector
import org.springframework.context.annotation.Bean

/** 为应用和 Flyway 配置共享数据源，并装配模块迁移与 MyBatis-Plus 分页支持。 */
@AutoConfiguration(
    before = [DataSourceAutoConfiguration::class, FlywayAutoConfiguration::class, MybatisPlusAutoConfiguration::class],
)
@EnableConfigurationProperties(DatabaseProperties::class)
internal class DatabaseAutoConfiguration {
    /** 记录选中的数据库类型和自动选择时无法连接的候选。 */
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 按启动模式创建应用与 Flyway 共用的连接池。
     *
     * @param properties 包含数据库选择模式和各候选连接信息的配置。
     * @return 使用选定数据库的 Hikari 连接池，由 Spring 在 Bean 销毁时关闭。
     */
    @Bean(destroyMethod = "close")
    @FlywayDataSource
    fun dataSource(properties: DatabaseProperties): HikariDataSource {
        val config = when (properties.mode) {
            DatabaseMode.SQLITE -> sqlitePool(properties.sqlite)
            DatabaseMode.POSTGRESQL -> remotePool(DatabaseDriver.POSTGRESQL, properties.postgresql)
            DatabaseMode.MYSQL -> remotePool(DatabaseDriver.MYSQL, properties.mysql)
            DatabaseMode.AUTO -> autoPool(properties)
        }
        val vendor = DatabaseDriver.fromJdbcUrl(config.jdbcUrl).id
        return HikariDataSource(config).also { logger.info("Selected database: {}", vendor) }
    }

    /**
     * 根据实际 JDBC URL 选择 Flyway 核心迁移的共用目录和数据库专用目录。
     *
     * @param dataSource 应用选定的共享连接池。
     * @return 将 Flyway 迁移位置设为 `db/migration/common` 和 `db/migration/<数据库标识>` 的定制器。
     */
    @Bean
    fun zeroFlywayConfiguration(dataSource: HikariDataSource): FlywayConfigurationCustomizer {
        val vendor = DatabaseDriver.fromJdbcUrl(dataSource.jdbcUrl).id
        return FlywayConfigurationCustomizer { configuration ->
            configuration.locations("classpath:db/migration/common", "classpath:db/migration/$vendor")
        }
    }

    /**
     * 先运行核心迁移，再按模块名排序执行模块迁移。模块名重复时在迁移开始前报错。
     * 每个模块使用 `db/modules/<模块名>` 和独立的 `zero_history_<模块名>` 历史表，
     * 允许在非空库上以版本 0 为基线。
     *
     * @param modules 当前应用装配的模块迁移声明。
     * @return 执行核心迁移及模块迁移的 Flyway 策略。
     */
    @Bean
    @ConditionalOnMissingBean(FlywayMigrationStrategy::class)
    fun migrateInstalledModules(modules: ObjectProvider<ModuleMigration>): FlywayMigrationStrategy =
        FlywayMigrationStrategy { core ->
            val names = modules.stream().map { it.name }.sorted().toList()
            require(names.distinct().size == names.size) { "Duplicate database migration module name" }
            core.migrate()
            for (name in names) {
                // 每个可选模块单独校验历史，暂时卸载后可以在原库上重新启用。
                Flyway.configure(core.configuration.classLoader)
                    .configuration(core.configuration)
                    .locations("classpath:db/modules/$name")
                    .table("zero_history_$name")
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .load()
                    .migrate()
            }
        }

    /**
     * 注册分页拦截器，由它根据实际数据源识别方言。
     *
     * @return 包含分页拦截器的 MyBatis-Plus 拦截器。
     */
    @Bean
    fun mybatisPlusInterceptor(): MybatisPlusInterceptor = MybatisPlusInterceptor().apply {
        addInnerInterceptor(PaginationInnerInterceptor())
    }

    /** 让 MyBatis-Plus 将 UUID 主键作为单个 JDBC 参数绑定。 */
    @Bean
    fun uuidTypeHandler(): UuidTypeHandler = UuidTypeHandler()

    /**
     * 解析 SQLite 文件的绝对路径并创建父目录。
     *
     * @param properties SQLite 文件路径配置。
     * @return 最多使用一个连接，并在创建连接时启用外键检查的连接池配置。
     */
    private fun sqlitePool(properties: SqliteProperties): HikariConfig {
        require(properties.path.isNotBlank()) { "zero.database.sqlite.path must be a file path" }
        val file = Path.of(properties.path).toAbsolutePath().normalize()
        require(file.fileName != null) { "zero.database.sqlite.path must be a file path" }
        Files.createDirectories(file.parent)
        return HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${file.toUri()}"
            connectionInitSql = "PRAGMA foreign_keys = ON"
            maximumPoolSize = 1
        }
    }

    /**
     * 依次探测配置了 URL 的 PostgreSQL 和 MySQL，未找到可达候选时选用 SQLite。
     * 探测候选端点前会校验其 JDBC URL，校验失败会终止启动。
     * 发现可达端点即选定数据源，后续 JDBC 登录或迁移失败会使启动失败。
     *
     * @param properties 候选数据库配置和每个 IP 地址的 TCP 探测超时。
     * @return 首个 TCP 可达的远端连接池配置，或 SQLite 连接池配置。
     */
    private fun autoPool(properties: DatabaseProperties): HikariConfig {
        val timeoutMillis = properties.probeTimeout.toMillis()
        require(timeoutMillis in 1L..Int.MAX_VALUE.toLong()) {
            "zero.database.probe-timeout must be between 1 ms and ${Int.MAX_VALUE} ms"
        }
        val candidates = listOf(
            DatabaseDriver.POSTGRESQL to properties.postgresql,
            DatabaseDriver.MYSQL to properties.mysql,
        )
        for ((driver, remote) in candidates) {
            val url = remote.url ?: continue
            val endpoint = endpoint(driver, url)
            val port = if (endpoint.port == -1) {
                if (driver == DatabaseDriver.POSTGRESQL) 5432 else 3306
            } else {
                endpoint.port
            }
            if (canConnect(endpoint.host, port, timeoutMillis.toInt())) {
                return remotePool(remote, url)
            }
            logger.warn("Database TCP endpoint unreachable in auto mode: {}", driver.id)
        }
        return sqlitePool(properties.sqlite)
    }

    /**
     * 校验显式远端模式的 JDBC URL 后组装连接池配置。
     *
     * @param driver 显式选中的 PostgreSQL 或 MySQL 驱动。
     * @param properties 含必填 URL 和可选凭证的远端配置。
     * @return 指向指定远端数据库的连接池配置。
     */
    private fun remotePool(driver: DatabaseDriver, properties: RemoteDatabaseProperties): HikariConfig {
        val url = requireNotNull(properties.url) { "zero.database.${driver.id}.url is required" }
        endpoint(driver, url)
        return remotePool(properties, url)
    }

    /**
     * 将已校验的 JDBC URL 与凭证传给 Hikari。
     *
     * @param properties 提供可选用户名和密码的远端配置。
     * @param url 已按驱动校验的原始 JDBC URL，保留连接参数。
     * @return 远端数据库连接池配置。
     */
    private fun remotePool(properties: RemoteDatabaseProperties, url: String): HikariConfig = HikariConfig().apply {
        jdbcUrl = url
        username = properties.username
        password = properties.password
    }

    /**
     * 校验单主机 JDBC URL，并将其解析为可供 TCP 探测的 URI。
     *
     * @param driver 决定 JDBC URL 前缀的驱动。
     * @param url 含主机和数据库路径的 JDBC URL，允许查询参数，禁止嵌入用户信息或片段。
     * @return 去掉 `jdbc:` 前缀的 URI。未指定端口时，其端口值为 `-1`。
     * @throws IllegalArgumentException URL 的格式、驱动前缀、端口或数据库路径无效。
     */
    private fun endpoint(driver: DatabaseDriver, url: String): URI {
        val error = "zero.database.${driver.id}.url must use jdbc:${driver.id}://host[:port]/database"
        val uri = try {
            URI(url.removePrefix("jdbc:"))
        } catch (_: URISyntaxException) {
            throw IllegalArgumentException(error)
        }
        require(
            url.startsWith("jdbc:${driver.id}://") && uri.host != null &&
            (uri.port == -1 || uri.port in 1..65535) && uri.rawUserInfo == null &&
            uri.rawFragment == null && (uri.rawPath?.length ?: 0) > 1,
        ) { error }
        return uri
    }

    /**
     * 依次尝试连接主机解析出的 IP 地址，连接成功后立即关闭探测套接字。
     *
     * @param host 从 JDBC URL 提取的主机名或 IP。
     * @param port JDBC URL 中的端口或数据库的默认端口。
     * @param timeoutMillis 每个地址的 TCP 连接超时，单位为毫秒。
     * @return 任一地址可连接时为 `true`。解析失败或所有地址均遇到连接拒绝、超时、路由或网络不可达时为 `false`。
     */
    private fun canConnect(host: String, port: Int, timeoutMillis: Int): Boolean {
        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (_: UnknownHostException) {
            return false
        }
        return addresses.any { address ->
            try {
                Socket().use { it.connect(InetSocketAddress(address, port), timeoutMillis) }
                true
            } catch (_: SocketTimeoutException) {
                false
            } catch (_: ConnectException) {
                false
            } catch (_: NoRouteToHostException) {
                false
            } catch (failure: SocketException) {
                // Unix JDK 25 将 ENETUNREACH 映射为普通 SocketException。
                if (failure.message?.contains("network is unreachable", ignoreCase = true) != true) throw failure
                false
            }
        }
    }
}

/** 告诉 Spring 先完成数据库初始化，再创建 MyBatis 会话工厂。 */
internal class MybatisDatabaseInitializationDetector : AbstractBeansOfTypeDependsOnDatabaseInitializationDetector() {
    /**
     * 声明依赖数据库初始化的 MyBatis Bean 类型。
     *
     * @return 仅包含 [SqlSessionFactory] 的类型集合。
     */
    override fun getDependsOnDatabaseInitializationBeanTypes(): Set<Class<*>> = setOf(SqlSessionFactory::class.java)
}
