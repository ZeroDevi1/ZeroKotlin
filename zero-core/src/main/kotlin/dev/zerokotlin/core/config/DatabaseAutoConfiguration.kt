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

@AutoConfiguration(
    before = [DataSourceAutoConfiguration::class, FlywayAutoConfiguration::class, MybatisPlusAutoConfiguration::class],
)
@EnableConfigurationProperties(DatabaseProperties::class)
internal class DatabaseAutoConfiguration {
    private val logger = LoggerFactory.getLogger(javaClass)

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

    @Bean
    fun zeroFlywayConfiguration(dataSource: HikariDataSource): FlywayConfigurationCustomizer {
        val vendor = DatabaseDriver.fromJdbcUrl(dataSource.jdbcUrl).id
        return FlywayConfigurationCustomizer { configuration ->
            configuration.locations("classpath:db/migration/common", "classpath:db/migration/$vendor")
        }
    }

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

    @Bean
    fun mybatisPlusInterceptor(): MybatisPlusInterceptor = MybatisPlusInterceptor().apply {
        addInnerInterceptor(PaginationInnerInterceptor())
    }

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

    private fun remotePool(driver: DatabaseDriver, properties: RemoteDatabaseProperties): HikariConfig {
        val url = requireNotNull(properties.url) { "zero.database.${driver.id}.url is required" }
        endpoint(driver, url)
        return remotePool(properties, url)
    }

    private fun remotePool(properties: RemoteDatabaseProperties, url: String): HikariConfig = HikariConfig().apply {
        jdbcUrl = url
        username = properties.username
        password = properties.password
    }

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

internal class MybatisDatabaseInitializationDetector : AbstractBeansOfTypeDependsOnDatabaseInitializationDetector() {
    override fun getDependsOnDatabaseInitializationBeanTypes(): Set<Class<*>> = setOf(SqlSessionFactory::class.java)
}
