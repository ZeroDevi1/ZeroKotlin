package dev.zerokotlin.core.config

import java.sql.CallableStatement
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID
import org.apache.ibatis.type.BaseTypeHandler
import org.apache.ibatis.type.JdbcType
import org.apache.ibatis.type.MappedTypes

/** 读写由 JDBC 驱动处理的原生 UUID 列。 */
@MappedTypes(UUID::class)
internal class UuidTypeHandler : BaseTypeHandler<UUID>() {
    override fun setNonNullParameter(statement: PreparedStatement, index: Int, value: UUID, jdbcType: JdbcType?) {
        statement.setObject(index, value)
    }

    override fun getNullableResult(result: ResultSet, column: String): UUID? = result.getObject(column, UUID::class.java)

    override fun getNullableResult(result: ResultSet, column: Int): UUID? = result.getObject(column, UUID::class.java)

    override fun getNullableResult(statement: CallableStatement, column: Int): UUID? = statement.getObject(column, UUID::class.java)
}
