package dev.zerokotlin.ktor

import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import java.nio.file.Files
import java.nio.file.Path
import java.sql.SQLException
import java.util.Locale

internal class DatabaseSettings(private val config: ApplicationConfig) {
    private fun value(name: String) = config.propertyOrNull("zero.$name")?.getString()

    fun connect(): Pair<String, HikariDataSource> {
        val mode = checkNotNull(value("databaseMode")).lowercase(Locale.ROOT)
        if (mode == "sqlite") return "sqlite" to sqlite()
        if (mode != "auto") {
            require(mode in setOf("postgresql", "mysql")) { "ZERO_DATABASE_MODE 须为 auto、sqlite、postgresql 或 mysql" }
            return mode to remote(mode, requireNotNull(value("${mode}Url")) { "所选数据库缺少 JDBC URL" })
        }
        for (vendor in listOf("postgresql", "mysql")) {
            val url = value("${vendor}Url")?.takeIf(String::isNotBlank) ?: continue
            try {
                return vendor to remote(vendor, url)
            } catch (failure: Exception) {
                // 只在连接不可达时尝试下一个候选；不掩盖凭据或迁移错误。
                val states = generateSequence<Throwable>(failure) { it.cause }
                    .filterIsInstance<SQLException>().mapNotNull { it.sqlState }.toList()
                if (states.none { it.startsWith("08") } || states.any { it.startsWith("28") }) throw failure
            }
        }
        return "sqlite" to sqlite()
    }

    private fun sqlite(): HikariDataSource {
        val path = Path.of(checkNotNull(value("databasePath"))).toAbsolutePath().normalize()
        Files.createDirectories(path.parent)
        val sqlite = SQLiteDataSource(SQLiteConfig().apply {
            enforceForeignKeys(true)
            setBusyTimeout(5000)
        }).apply { url = "jdbc:sqlite:$path" }
        return HikariDataSource().apply { dataSource = sqlite; maximumPoolSize = 4 }
    }

    private fun remote(vendor: String, url: String): HikariDataSource {
        require(url.startsWith("jdbc:$vendor://")) { "JDBC URL 与所选数据库不匹配" }
        val pool = HikariDataSource().apply {
            jdbcUrl = url
            value("${vendor}Username")?.let { username = it }
            value("${vendor}Password")?.let { password = it }
            connectionTimeout = 3000
            validationTimeout = 2000
        }
        return try {
            pool.connection.use { /* 在启动迁移前确认连接和凭据。 */ }
            pool
        } catch (failure: Exception) {
            pool.close()
            throw failure
        }
    }
}
