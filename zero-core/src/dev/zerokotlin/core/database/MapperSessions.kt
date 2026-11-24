package dev.zerokotlin.core.database

import com.baomidou.mybatisplus.core.MybatisConfiguration
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor
import org.apache.ibatis.mapping.Environment
import org.apache.ibatis.session.ExecutorType
import org.apache.ibatis.session.LocalCacheScope
import org.apache.ibatis.session.SqlSession
import org.apache.ibatis.transaction.managed.ManagedTransactionFactory
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.CallableStatement
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.Properties
import java.util.UUID
import javax.sql.DataSource
import org.apache.ibatis.type.BaseTypeHandler
import org.apache.ibatis.type.JdbcType

/** Ktor 启动时装配 MyBatis-Plus。Mapper 执行期间沿用调用方已经打开的 JDBC 事务。 */
class MapperSessions(dataSource: DataSource, vendor: String, mappers: Iterable<Class<*>>) {
    private val factory = MybatisSqlSessionFactoryBuilder().build(
        MybatisConfiguration(Environment(vendor, ManagedTransactionFactory().apply {
            // SqlSession 只管理 statement；外层事务负责提交、回滚和归还连接。
            setProperties(Properties().apply { setProperty("closeConnection", "false") })
        }, dataSource)).apply {
            isCacheEnabled = false
            localCacheScope = LocalCacheScope.STATEMENT
            addInterceptor(MybatisPlusInterceptor().apply { addInnerInterceptor(PaginationInnerInterceptor()) })
            val sqlite = vendor == "sqlite"
            typeHandlerRegistry.register(LocalDate::class.java, JdbcValue(LocalDate::class.java, sqlite, LocalDate::parse))
            typeHandlerRegistry.register(LocalTime::class.java, JdbcValue(LocalTime::class.java, sqlite, LocalTime::parse))
            typeHandlerRegistry.register(LocalDateTime::class.java, JdbcValue(LocalDateTime::class.java, sqlite, LocalDateTime::parse))
            typeHandlerRegistry.register(OffsetDateTime::class.java, JdbcValue(OffsetDateTime::class.java, sqlite, OffsetDateTime::parse))
            typeHandlerRegistry.register(UUID::class.java, JdbcValue(UUID::class.java, sqlite, UUID::fromString))
            mappers.forEach { addMapper(it) }
        },
    )

    /** Session 和 Mapper 代理仅在 [action] 内有效；SIMPLE 执行器立即执行写入。 */
    fun <T> withSession(connection: Connection, action: (SqlSession) -> T): T =
        factory.openSession(ExecutorType.SIMPLE, connection).use(action)
}

/** SQLite 按文本保存日期、时间和 UUID，其他数据库由 JDBC 处理原生类型。 */
private class JdbcValue<T : Any>(
    private val type: Class<T>,
    private val sqlite: Boolean,
    private val parse: (String) -> T,
) : BaseTypeHandler<T>() {
    override fun setNonNullParameter(statement: PreparedStatement, index: Int, parameter: T, jdbcType: JdbcType?) {
        if (sqlite) statement.setString(index, parameter.toString()) else statement.setObject(index, parameter)
    }

    override fun getNullableResult(result: ResultSet, column: String): T? =
        if (sqlite) result.getString(column)?.let(parse) else result.getObject(column, type)

    override fun getNullableResult(result: ResultSet, index: Int): T? =
        if (sqlite) result.getString(index)?.let(parse) else result.getObject(index, type)

    override fun getNullableResult(statement: CallableStatement, index: Int): T? =
        if (sqlite) statement.getString(index)?.let(parse) else statement.getObject(index, type)
}
