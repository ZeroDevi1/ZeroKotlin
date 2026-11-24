package dev.zerokotlin.codegen

/** 业务包里的 Controller、Service、ServiceImpl 与 Mapper 由同一份表元数据生成。 */
internal fun renderResource(resource: ResourceMetadata): Map<String, String> = with(resource) {
    val base = "src/${packageName.replace('.', '/')}"
    mapOf(
        "$base/entity/${name}Record.kt" to recordSource(),
        "$base/mapper/${name}Mapper.kt" to mapperSource(),
        "$base/service/${name}Service.kt" to serviceSource(),
        "$base/service/impl/${name}ServiceImpl.kt" to serviceImplSource(),
        "$base/controller/${name}Controller.kt" to controllerSource(),
    )
}

private fun ResourceMetadata.recordSource() = buildString {
    appendLine("package $packageName.entity")
    appendLine()
    appendLine("import com.baomidou.mybatisplus.annotation.FieldStrategy")
    appendLine("import com.baomidou.mybatisplus.annotation.IdType")
    appendLine("import com.baomidou.mybatisplus.annotation.TableField")
    appendLine("import com.baomidou.mybatisplus.annotation.TableId")
    appendLine("import com.baomidou.mybatisplus.annotation.TableName")
    appendLine()
    appendLine("/** $name 的数据库映射。字段说明来自 JDBC 列注释或原始列名。 */")
    appendLine("@TableName(value = ${literal(sqlTable)}, autoResultMap = true)")
    appendLine("internal class ${name}Record {")
    columns.forEach { column ->
        val name = literal(database.quote(column.sqlName))
        val type = when {
            column.type == ColumnType.BIG_INTEGER -> "java.math.BigDecimal"
            database == CodegenDatabase.SQLITE && column.type in sqliteTextColumns -> "String"
            else -> column.type.kotlin
        }
        appendLine("    /** ${column.description.kdoc()} */")
        if (column == key) {
            val idType = if (column.identity) "AUTO" else "INPUT"
            appendLine("    @field:TableId(value = $name, type = IdType.$idType)")
        } else {
            val insert = when {
                column.computed || column.identity -> "NEVER"
                column.hasDefault -> "NOT_NULL"
                else -> "ALWAYS"
            }
            val update = if (column in updateColumns) "ALWAYS" else "NEVER"
            appendLine("    @field:TableField(value = $name, insertStrategy = FieldStrategy.$insert, updateStrategy = FieldStrategy.$update)")
        }
        appendLine("    var ${column.property}: $type? = null")
        appendLine()
    }
    appendLine("}")
}

private fun ResourceMetadata.mapperSource() = buildString {
    appendLine("package $packageName.mapper")
    appendLine()
    appendLine("import $packageName.entity.${name}Record")
    appendLine("import com.baomidou.mybatisplus.core.mapper.BaseMapper")
    if (key.identity) {
        appendLine("import org.apache.ibatis.annotations.Insert")
        appendLine("import org.apache.ibatis.annotations.Options")
    }
    appendLine()
    appendLine("/** 单表 CRUD 由 MyBatis-Plus 提供。可在此声明使用固定 SQL 的业务查询。 */")
    append("internal interface ${name}Mapper : BaseMapper<${name}Record>")
    if (key.identity) {
        val sql = if (database == CodegenDatabase.MYSQL) "INSERT INTO $sqlTable () VALUES ()" else "INSERT INTO $sqlTable DEFAULT VALUES"
        appendLine(" {")
        appendLine("    /** 当请求不含可写列时，由数据库补齐默认值并回填主键。 */")
        appendLine("    @Insert(${literal(sql)})")
        appendLine("    @Options(useGeneratedKeys = true, keyProperty = ${literal(key.property)}, keyColumn = ${literal(key.sqlName)})")
        appendLine("    fun insertDefault(record: ${name}Record): Int")
        appendLine("}")
    } else appendLine()
}

private fun ResourceMetadata.serviceSource() = """
    package $packageName.service

    import dev.zerokotlin.ktor.ResourceService

    /**
     * $name 业务契约。权限、事务及对外可见范围在对应的 ServiceImpl 中处理。
     * 在这里追加本资源特有的业务操作，Controller 通过此接口调用。
     */
    internal interface ${name}Service : ResourceService
""".trimIndent() + "\n"

private fun ResourceMetadata.serviceImplSource() = buildString {
    appendLine("package $packageName.service.impl")
    appendLine()
    appendLine("import $packageName.entity.${name}Record")
    appendLine("import $packageName.mapper.${name}Mapper")
    appendLine("import $packageName.service.${name}Service")
    appendLine("import dev.zerokotlin.ktor.DefaultResourceService")
    appendLine("import dev.zerokotlin.ktor.MybatisResourceMapper")
    appendLine("import dev.zerokotlin.ktor.ResourceColumn")
    appendLine("import dev.zerokotlin.ktor.ResourceKind")
    appendLine("import dev.zerokotlin.ktor.ResourceSpec")
    appendLine("import dev.zerokotlin.ktor.SystemStore")
    appendLine("import dev.zerokotlin.ktor.resourceProperty")
    appendLine()
    appendLine("/** 装配业务权限和 Entity 绑定。BaseMapper 复用外层事务，写入与审计一并提交。 */")
    appendLine("internal class ${name}ServiceImpl(store: SystemStore) : DefaultResourceService(")
    appendLine("    store, spec, MybatisResourceMapper(")
    appendLine("        store.mappers, spec, ${name}Mapper::class.java, ::${name}Record,")
    appendLine("        listOf(")
    columns.forEach { column -> appendLine("            resourceProperty(${name}Record::${column.property}),") }
    appendLine("        ),")
    if (key.identity) appendLine("        insertDefault = ${name}Mapper::insertDefault,")
    appendLine("    ),")
    appendLine("), ${name}Service {")
    appendLine("    // 有归属或租户要求时，在此覆盖 rowScope 和 prepareCreate，限制查询及批量导入。")
    appendLine("    private companion object {")
    appendLine("        val spec = ResourceSpec(")
    appendLine("            table = ${literal(sqlTable)},")
    appendLine("            mysql = ${database == CodegenDatabase.MYSQL},")
    if (database == CodegenDatabase.SQLITE) appendLine("            sqliteText = true,")
    appendLine("            authority = ${literal(permissions.getValue("read").removeSuffix(":read"))},")
    appendLine("            columns = listOf(")
    columns.forEach { column ->
        appendLine("                // ${column.description.kdoc()}")
        appendLine(
            "                ResourceColumn(${literal(column.property)}, ${literal(database.quote(column.sqlName))}, " +
                "${literal(column.description.take(160).trimEnd { it.isHighSurrogate() })}, ResourceKind.${column.type.name}, ${column.nullable}, " +
                "${column.identity}, ${column.computed}, ${column.hasDefault}),",
        )
    }
    appendLine("            ),")
    appendLine("        )")
    appendLine("    }")
    appendLine("}")
}

private fun ResourceMetadata.controllerSource() = buildString {
    appendLine("package $packageName.controller")
    appendLine()
    appendLine("import $packageName.service.${name}Service")
    appendLine("import dev.zerokotlin.ktor.ResourceHttp")
    appendLine("import io.ktor.server.routing.Route")
    appendLine("import io.ktor.server.routing.delete")
    appendLine("import io.ktor.server.routing.get")
    appendLine("import io.ktor.server.routing.post")
    if (updateColumns.isNotEmpty()) appendLine("import io.ktor.server.routing.put")
    appendLine("import io.ktor.server.routing.route")
    appendLine()
    appendLine("/** 挂载 $apiPath。HTTP 参数及状态经 ResourceHttp 处理，业务调用进入 ${name}Service。 */")
    appendLine("internal fun Route.register${name}Routes(service: ${name}Service) {")
    appendLine("    val http = ResourceHttp(service)")
    appendLine("    route(${literal(apiPath.removePrefix("/api/"))}) {")
    fun routeDoc(title: String, access: String, vararg outcomes: String, indent: String = "        ") {
        appendLine("$indent/**")
        appendLine("$indent * $title。")
        appendLine("$indent *")
        appendLine("$indent * Tag: $name")
        appendLine("$indent * Description: 需要 ${permissions.getValue(access)} 权限。")
        appendLine("$indent * Security: bearer")
        outcomes.forEach { appendLine("$indent * Response: $it") }
        appendLine("$indent */")
    }

    routeDoc("查询列表", "read", "200 分页结果，可使用 filter.<属性名> 筛选")
    appendLine("        get { http.list(call) }")
    routeDoc("查看详情", "read", "200 单条记录", "404 记录不存在或超出数据范围")
    appendLine("        get(\"{id}\") { http.find(call) }")
    routeDoc("导出 Excel", "read", "200 XLSX 文件，最多 1000 条")
    appendLine("        get(\"excel/export\") { http.export(call) }")
    routeDoc("新增记录", "create", "201 新记录的主键", "409 主键或唯一字段冲突")
    appendLine("        post { http.create(call) }")
    if (importColumns.isNotEmpty()) {
        appendLine("        if (service.importColumns.isNotEmpty()) {")
        routeDoc("下载 Excel 导入模板", "create", "200 XLSX 模板", indent = "            ")
        appendLine("            get(\"excel/template\") { http.template(call) }")
        routeDoc("批量导入 Excel", "create", "201 导入数量", "400 内容不符合导入约束，整批回滚", indent = "            ")
        appendLine("            post(\"excel/import\") { http.importRows(call) }")
        appendLine("        }")
    }
    if (updateColumns.isNotEmpty()) {
        appendLine("        if (service.supportsUpdate) {")
        routeDoc("更新记录", "update", "204 更新成功", "404 记录不存在或超出数据范围", indent = "            ")
        appendLine("            put(\"{id}\") { http.update(call) }")
        appendLine("        }")
    }
    routeDoc("删除记录", "delete", "204 删除成功", "404 记录不存在或超出数据范围")
    appendLine("        delete(\"{id}\") { http.delete(call) }")
    appendLine("    }")
    appendLine("}")
}

private val sqliteTextColumns = setOf(ColumnType.DATE, ColumnType.TIME, ColumnType.TIMESTAMP, ColumnType.TIMESTAMP_TZ, ColumnType.UUID)

private fun String.kdoc(): String = replace("/*", "/ *").replace("*/", "* /")

private fun literal(value: String): String = buildString {
    append('"')
    for (char in value) when (char) {
        '\\', '"', '$' -> append('\\').append(char)
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> append(char)
    }
    append('"')
}
