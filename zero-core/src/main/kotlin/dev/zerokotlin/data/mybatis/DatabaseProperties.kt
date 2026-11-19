package dev.zerokotlin.data.mybatis

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("zero.database")
internal class DatabaseProperties(
    val mode: DatabaseMode = DatabaseMode.AUTO,
    val sqlite: SqliteProperties = SqliteProperties(),
    val postgresql: RemoteDatabaseProperties = RemoteDatabaseProperties(),
    val mysql: RemoteDatabaseProperties = RemoteDatabaseProperties(),
    val probeTimeout: Duration = Duration.ofSeconds(1),
)

internal enum class DatabaseMode { SQLITE, POSTGRESQL, MYSQL, AUTO }

internal class SqliteProperties(val path: String = "data/zero-kotlin.db")

internal class RemoteDatabaseProperties(
    val url: String? = null,
    val username: String? = null,
    val password: String? = null,
)
