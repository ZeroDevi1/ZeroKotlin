package dev.zerokotlin.ktor

import com.zaxxer.hikari.HikariDataSource
import dev.zerokotlin.core.SystemFailure
import dev.zerokotlin.core.database.DatabaseSettings
import dev.zerokotlin.core.database.MapperSessions
import dev.zerokotlin.ktor.system.audit.recordMonitorRequest
import dev.zerokotlin.ktor.system.auth.CurrentUser
import dev.zerokotlin.ktor.system.auth.actor
import dev.zerokotlin.ktor.system.auth.audit
import dev.zerokotlin.ktor.system.auth.requireAccount
import dev.zerokotlin.ktor.system.auth.requireAccountDigest
import java.sql.Connection
import java.sql.ResultSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.flywaydb.core.Flyway

@Serializable
internal data class Page<T>(val items: List<T>, val total: Long, val page: Int, val size: Int)

internal fun Connection.change(sql: String, vararg args: Any?): Int =
    prepareStatement(sql).use { statement ->
        args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        statement.executeUpdate()
    }

internal fun <T> Connection.rows(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { statement ->
        args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        statement.executeQuery().use { result -> buildList { while (result.next()) add(map(result)) } }
    }

/** 拥有数据源及事务。业务读写在授权后使用同一连接操作数据和审计。 */
internal class SystemStore(
    settings: DatabaseSettings,
    internal val sessionSeconds: Long,
    internal val modules: Set<String>,
    private val sanitizeText: Boolean,
    mapperTypes: Set<Class<*>> = emptySet(),
) : AutoCloseable {
    private val database: HikariDataSource
    internal val mappers: MapperSessions

    init {
        require(sessionSeconds in 1..604800) { "会话有效期须为 1 至 604800 秒" }
        val (vendor, source) = settings.connect()
        database = source
        try {
            val core = Flyway.configure().dataSource(source)
                .locations("classpath:db/migration/common", "classpath:db/migration/$vendor")
                // 保留原 V9 的校验和；Ktor 始终允许 SQL 在空用户表中创建默认账号。
                .placeholders(mapOf("zeroSkipDefaultAdmin" to "false")).load()
            core.migrate()
            // 每个业务模块独立记录版本，允许它们各自从 V1 开始。
            for (module in modules.sorted()) {
                Flyway.configure(core.configuration.classLoader).configuration(core.configuration)
                    .locations("classpath:db/modules/$module").table("zero_history_$module")
                    .baselineOnMigrate(true).baselineVersion("0").load().migrate()
            }
            mappers = MapperSessions(source, vendor, mapperTypes)
        } catch (failure: Exception) {
            runCatching { source.close() }.onFailure(failure::addSuppressed)
            throw failure
        }
    }

    fun plainName(value: String): String {
        val text = checkedName(value)
        return if (sanitizeText && ('<' in text || '&' in text)) {
            checkedName(org.jsoup.safety.Cleaner(org.jsoup.safety.Safelist.none())
                .clean(org.jsoup.Jsoup.parseBodyFragment(text)).body().wholeText())
        } else text
    }

    /** 写事务先占用系统写锁，确保权限检查、业务变更和审计按同一顺序提交。 */
    internal fun <T> transaction(write: Boolean = false, action: (Connection) -> T): T = database.connection.use { db ->
        db.autoCommit = false
        try {
            // 复用系统写锁，先加锁再复核会话和密码快照。
            if (write) check(db.change("UPDATE sys_write_guard SET revision = revision + 1 WHERE id = 'system'") == 1)
            action(db).also { db.commit() }
        } catch (failure: Throwable) {
            try { db.rollback() } catch (rollback: Exception) { failure.addSuppressed(rollback) }
            throw failure
        }
    }

    /** 校验会话与操作权限，行级过滤由业务查询负责。 */
    internal suspend fun <T> read(
        raw: String,
        authority: String?,
        action: (Connection, CurrentUser) -> T,
    ): T = withContext(Dispatchers.IO) {
        transaction { db ->
            val actor = db.actor(db.requireAccount(raw))
            if (authority != null && authority !in actor.permissions) throw SystemFailure(403, "没有权限")
            action(db, actor)
        }
    }

    /** 在同一写事务内复核会话和权限，并随业务变更提交审计。 */
    internal suspend fun <T> write(
        raw: String,
        authority: String,
        administratorOnly: Boolean = false,
        action: (Connection, CurrentUser, (String, String) -> Unit) -> T,
    ): T = withContext(Dispatchers.IO) {
        transaction(write = true) { db ->
            val account = db.requireAccount(raw)
            val actor = db.actor(account)
            if (authority !in actor.permissions || (administratorOnly && !actor.administrator)) {
                throw SystemFailure(403, "没有权限")
            }
            action(db, actor) { name, target -> db.audit(account, name, target) }
        }
    }

    suspend fun monitorAudit(actorId: String?, status: Int, tookMs: Long, exception: String?, address: String) =
        withContext(Dispatchers.IO) {
            transaction { db -> db.recordMonitorRequest(actorId, status, tookMs, exception, address) }
        }

    suspend fun currentDigest(sessionDigest: String) = withContext(Dispatchers.IO) {
        transaction { db -> db.actor(db.requireAccountDigest(sessionDigest)) }
    }

    suspend fun current(raw: String) = withContext(Dispatchers.IO) {
        transaction { db -> db.actor(db.requireAccount(raw)) }
    }

    suspend fun healthy(): Boolean = withContext(Dispatchers.IO) {
        try { transaction { db -> db.rows("SELECT 1") { it.getInt(1) }.singleOrNull() == 1 } }
        catch (_: java.sql.SQLException) { false }
    }

    override fun close() = database.close()
}

internal fun checkedName(value: String): String =
    value.trim().takeIf { it.isNotEmpty() && it.length <= 120 && it.none { ch -> Character.isISOControl(ch) } }
        ?: throw SystemFailure(400, "名称须为 1 至 120 个字符")
