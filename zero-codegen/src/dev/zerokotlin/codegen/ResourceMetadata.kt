package dev.zerokotlin.codegen

import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.Types

/** 生成器支持的 JDBC URL 前缀与 SQL 标识符引用方式。 */
internal enum class CodegenDatabase(val urlPrefix: String, private val quote: Char) {
    SQLITE("jdbc:sqlite:", '"'),
    POSTGRESQL("jdbc:postgresql://", '"'),
    MYSQL("jdbc:mysql://", '`');

    /** 为已验证的标识符加上方言引号，保留大小写并支持 SQL 保留字。 */
    fun quote(name: String): String = "$quote$name$quote"
}

/** Kotlin 类型和绑定 SQL 参数时使用的 JDBC 类型。 */
internal enum class ColumnType(
    val kotlin: String,
    val jdbcType: String,
) {
    BOOLEAN("Boolean", "BOOLEAN"),
    INT("Int", "INTEGER"),
    LONG("Long", "BIGINT"),
    BIG_INTEGER("java.math.BigInteger", "DECIMAL"),
    FLOAT("Float", "REAL"),
    DOUBLE("Double", "DOUBLE"),
    DECIMAL("java.math.BigDecimal", "DECIMAL"),
    STRING("String", "VARCHAR"),
    BYTES("ByteArray", "VARBINARY"),
    DATE("java.time.LocalDate", "DATE"),
    TIME("java.time.LocalTime", "TIME"),
    TIMESTAMP("java.time.LocalDateTime", "TIMESTAMP"),
    TIMESTAMP_TZ("java.time.OffsetDateTime", "TIMESTAMP_WITH_TIMEZONE"),
    UUID("java.util.UUID", "OTHER"),
}

/** 收集数据库列的声明类型、可空性、主键和数据库托管写入信息。 */
private data class ColumnMetadata(
    val name: String,
    val declaredType: String,
    val jdbcType: Int,
    val size: Int,
    val nullable: Boolean,
    val primaryKey: Boolean,
    val identity: Boolean,
    val computed: Boolean,
    val hasDefault: Boolean,
    val remarks: String? = null,
)

/** 已验证的列及其 Kotlin 属性。 */
internal data class ResourceColumn(
    val sqlName: String,
    val property: String,
    val type: ColumnType,
    val nullable: Boolean,
    val identity: Boolean,
    val computed: Boolean,
    val hasDefault: Boolean,
    val remarks: String?,
) {
    /** 字段说明优先沿用 JDBC 列注释。 */
    val description: String = remarks?.replace(Regex("[\\s\\p{Cntrl}\\p{Z}]+"), " ")?.trim()
        ?.takeIf(String::isNotEmpty) ?: "数据库列 $sqlName。"
}

/** 经校验的单表 CRUD 生成模型。 */
internal class ResourceMetadata(
    val database: CodegenDatabase,
    val packageName: String,
    val sqlTable: String,
    val name: String,
    val columns: List<ResourceColumn>,
) {
    val key = columns.first()
    val createColumns = columns.filterNot { it.identity || it.computed }
    val updateColumns = createColumns.filterNot { it == key }
    val importColumns = createColumns.filter { it.type != ColumnType.BYTES }.takeUnless {
        createColumns.any { it.type == ColumnType.BYTES && requiredOnCreate(it) }
    }.orEmpty()
    val namespace = packageName.removePrefix("dev.zerokotlin.")
    private val route = identifierWords(name).joinToString("-")
    val apiPath = "/api/${namespace.replace('.', '/')}/$route"
    val permissions = buildMap {
        put("read", "$namespace:$route:read")
        put("create", "$namespace:$route:create")
        if (updateColumns.isNotEmpty()) put("update", "$namespace:$route:update")
        put("delete", "$namespace:$route:delete")
    }

    /** 创建时必须提交的列。非自增主键始终由调用方提供。 */
    fun requiredOnCreate(column: ResourceColumn): Boolean =
        column == key || (!column.nullable && !column.hasDefault)

    /** 创建时按请求值决定是否让数据库填充该列。 */
    fun useDefaultOnCreate(column: ResourceColumn): Boolean = column != key && column.hasDefault
}

private val sqlIdentifier = Regex("[A-Za-z_][A-Za-z0-9_]*")
private val packageSegment = Regex("[a-z][a-z0-9_]*")
private val reservedWords = setOf(
    "as", "break", "catch", "class", "continue", "do", "else", "false", "finally", "for", "fun", "if",
    "in", "interface", "is", "null", "object", "package", "return", "super", "this", "throw", "true",
    "try", "typealias", "typeof", "val", "var", "when", "while",
)

/** 确保源数据库标识符能原样用于 SQL 引用及源代码。 */
internal fun checkedSqlName(name: String, label: String): String {
    require(name.matches(sqlIdentifier) && name.any { it != '_' }) {
        "$label 须由 ASCII 字母、数字及下划线组成，不能以数字开头或仅含下划线：$name"
    }
    return name
}

/** 目标包遵循业务代码的 Kotlin 命名空间约定。 */
internal fun checkedPackage(name: String): String {
    val prefix = "dev.zerokotlin."
    val segments = name.removePrefix(prefix).split('.')
    require(name.startsWith(prefix) && segments.all { it.matches(packageSegment) && it !in reservedWords }) {
        "--package 须使用 dev.zerokotlin 下的 Kotlin 小写包名"
    }
    return name
}

private val acronymBoundary = Regex("([A-Z]+)([A-Z][a-z])")
private val camelBoundary = Regex("([a-z0-9])([A-Z])")

private fun identifierWords(name: String): List<String> =
    name.replace(acronymBoundary, "$1_$2")
        .replace(camelBoundary, "$1_$2")
        .split('_')
        .filter(String::isNotEmpty)
        .map(String::lowercase)

private fun List<String>.upperCamel(): String = joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }

private fun propertyName(name: String): String {
    val words = identifierWords(checkedSqlName(name, "列名"))
    val property = words.first() + words.drop(1).upperCamel()
    return when {
        property.first().isDigit() -> "field${words.upperCamel()}"
        property in reservedWords -> "${property}Value"
        // 避开 Kotlin 对 isX 属性生成的特殊 JavaBeans getter。
        property.startsWith("is") && property.getOrNull(2)?.isLowerCase() == false -> "field${words.upperCamel()}"
        else -> property
    }
}

/** 读取且验证指定表。无主键、复合主键及无法可靠映射的列在写文件前报错。 */
internal fun readResource(connection: Connection, options: CodegenOptions): ResourceMetadata {
    val database = options.database
    val table = options.table
    val (qualifier, columns) = if (database == CodegenDatabase.SQLITE) {
        "main" to readSqliteColumns(connection, table)
    } else {
        readJdbcColumns(connection, options)
    }
    val keys = columns.filter(ColumnMetadata::primaryKey)
    require(keys.size == 1) { "表 $table 必须恰有一列主键，实际为 ${keys.size} 列" }
    val key = keys.single()
    require(!key.nullable && !key.computed) { "主键 ${key.name} 须为不可空且非计算列" }

    val resourceColumns = (listOf(key) + columns.filterNot(ColumnMetadata::primaryKey)).map { column ->
        ResourceColumn(
            column.name, propertyName(column.name), columnType(database, column),
            column.nullable, column.identity, column.computed, column.hasDefault, column.remarks,
        )
    }
    val duplicateProperties = resourceColumns.groupingBy(ResourceColumn::property).eachCount().filterValues { it > 1 }
    require(duplicateProperties.isEmpty()) { "列名转换后发生 Kotlin 属性名冲突：${duplicateProperties.keys.joinToString()}" }
    // 数据类的 copy 默认桥接方法还接收实例、参数位掩码及标记对象。
    val widePrimitives = resourceColumns.count { !it.nullable && (it.type == ColumnType.LONG || it.type == ColumnType.DOUBLE) }
    val copySlots = resourceColumns.size + widePrimitives + (resourceColumns.size + 31) / 32 + 2
    require(copySlots <= 255) { "表 $table 的列数和宽度超出 Kotlin 数据类的 JVM 参数上限" }
    val keyType = resourceColumns.first().type
    require(keyType in listOf(ColumnType.INT, ColumnType.LONG, ColumnType.BIG_INTEGER, ColumnType.STRING, ColumnType.UUID)) {
        "主键 ${key.name} 的类型 ${key.declaredType} 不支持用作 REST 资源标识"
    }
    require(!key.identity || keyType in listOf(ColumnType.INT, ColumnType.LONG, ColumnType.BIG_INTEGER)) {
        "数据库自增主键 ${key.name} 须为整数类型"
    }
    val name = options.classStem ?: identifierWords(table).upperCamel().let {
        if (it.first().isDigit()) "Table$it" else it
    }
    val sqlTable = listOfNotNull(qualifier, table).joinToString(".", transform = database::quote)
    return ResourceMetadata(database, options.packageName, sqlTable, name, resourceColumns).also { resource ->
        require(resource.permissions.values.all { it.length <= 120 }) { "权限编码超过系统限制 120 字符，请缩短包名或 --name" }
    }
}

private fun readSqliteColumns(connection: Connection, table: String): List<ColumnMetadata> {
    connection.prepareStatement("SELECT 1 FROM pragma_table_list WHERE schema = 'main' AND name = ? AND type = 'table'")
        .use { statement ->
            statement.setString(1, table)
            statement.executeQuery().use {
                require(it.next() && !table.startsWith("sqlite_", ignoreCase = true)) { "找不到 SQLite 普通表 main.$table" }
            }
        }
    // INTEGER PRIMARY KEY 只有在未另建主键索引时才是 rowid 的别名，包含无 AUTOINCREMENT 的情况。
    val hasPrimaryKeyIndex = connection.prepareStatement("SELECT 1 FROM pragma_index_list(?, 'main') WHERE origin = 'pk'")
        .use { statement ->
            statement.setString(1, table)
            statement.executeQuery().use { it.next() }
        }
    return connection.prepareStatement("SELECT * FROM pragma_table_xinfo(?, 'main') ORDER BY cid").use { statement ->
        statement.setString(1, table)
        statement.executeQuery().use { rows ->
            buildList {
                while (rows.next()) {
                    val type = rows.getString("type")
                    val key = rows.getInt("pk") > 0
                    val identity = key && !hasPrimaryKeyIndex && type.equals("INTEGER", ignoreCase = true)
                    add(
                        ColumnMetadata(
                            rows.getString("name"), type, Types.OTHER, 0,
                            nullable = rows.getInt("notnull") == 0 && !identity,
                            primaryKey = key,
                            identity = identity,
                            computed = rows.getInt("hidden") != 0,
                            hasDefault = rows.getString("dflt_value") != null,
                        ),
                    )
                }
            }
        }
    }
}

private fun readJdbcColumns(connection: Connection, options: CodegenOptions): Pair<String?, List<ColumnMetadata>> {
    val table = options.table
    val metadata = connection.metaData
    val postgres = options.database == CodegenDatabase.POSTGRESQL
    val useSchema = postgres || metadata.supportsSchemasInTableDefinitions()
    val namespace = if (postgres) options.schema ?: connection.schema else
        options.catalog ?: if (useSchema) connection.schema else connection.catalog
    val selection = checkedSqlName(requireNotNull(namespace) { "连接未选择数据库或 schema，请显式指定" }, "数据库或 schema")
    val catalog = if (postgres) connection.catalog else if (useSchema) null else selection
    val schema = selection.takeIf { useSchema }
    val schemaPattern = schema?.let { metadata.literalPattern(it) }
    val tablePattern = metadata.literalPattern(table)
    val tableTypes = if (postgres) arrayOf("TABLE", "PARTITIONED TABLE") else arrayOf("TABLE")
    metadata.getTables(catalog, schemaPattern, tablePattern, tableTypes).use { rows ->
        var found = false
        while (rows.next()) {
            if (rows.getString("TABLE_NAME") == table &&
                rows.getString(if (useSchema) "TABLE_SCHEM" else "TABLE_CAT") == selection
            ) {
                found = true
                break
            }
        }
        require(found) { "找不到普通表 $selection.$table，请核对名称和大小写" }
    }
    val keys = metadata.getPrimaryKeys(catalog, schema, table).use { rows ->
        buildSet {
            while (rows.next()) add(rows.getString("COLUMN_NAME"))
        }
    }
    val mysqlComputed = if (postgres) null else readMysqlComputedColumns(connection, selection, table)
    val columns = metadata.getColumns(catalog, schemaPattern, tablePattern, null).use { rows ->
        buildList {
            while (rows.next()) {
                if (rows.getString("TABLE_NAME") != table ||
                    rows.getString(if (useSchema) "TABLE_SCHEM" else "TABLE_CAT") != selection
                ) continue
                val name = rows.getString("COLUMN_NAME")
                val column = ColumnMetadata(
                    name, rows.getString("TYPE_NAME"), rows.getInt("DATA_TYPE"), rows.getInt("COLUMN_SIZE"),
                    nullable = rows.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                    primaryKey = name in keys,
                    identity = rows.getString("IS_AUTOINCREMENT") == "YES",
                    computed = mysqlComputed?.contains(name) ?: (rows.getString("IS_GENERATEDCOLUMN") == "YES"),
                    hasDefault = rows.getString("COLUMN_DEF") != null,
                    remarks = rows.getString("REMARKS"),
                )
                add(rows.getInt("ORDINAL_POSITION") to column)
            }
        }.sortedBy { it.first }.map { it.second }
    }
    require(columns.isNotEmpty() && keys.all { key -> columns.any { it.name == key } }) {
        "表 $selection.$table 的列或主键元数据不完整"
    }
    return (if (postgres || options.catalog != null) selection else null) to columns
}

private fun readMysqlComputedColumns(connection: Connection, catalog: String, table: String): Set<String> {
    // Connector/J 的 IS_GENERATEDCOLUMN 也可能包含带 DEFAULT_GENERATED 标记的普通列。
    val sql = """
        SELECT c.COLUMN_NAME, c.GENERATION_EXPRESSION, t.ENGINE
        FROM INFORMATION_SCHEMA.COLUMNS c
        JOIN INFORMATION_SCHEMA.TABLES t
            ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME
        WHERE c.TABLE_SCHEMA = ? AND c.TABLE_NAME = ?
    """.trimIndent()
    return connection.prepareStatement(sql).use { statement ->
        statement.setString(1, catalog)
        statement.setString(2, table)
        statement.executeQuery().use { rows ->
            require(rows.next()) { "表 $catalog.$table 的计算列元数据不可见" }
            require(rows.getString("ENGINE").equals("InnoDB", ignoreCase = true)) {
                "MySQL 表 $catalog.$table 须使用 InnoDB，以保证业务写入可和审计一起回滚"
            }
            buildSet {
                do {
                    if (!rows.getString("GENERATION_EXPRESSION").isNullOrEmpty()) add(rows.getString("COLUMN_NAME"))
                } while (rows.next())
            }
        }
    }
}

private fun DatabaseMetaData.literalPattern(identifier: String): String =
    identifier.replace("_", "${searchStringEscape}_")

private fun columnType(database: CodegenDatabase, column: ColumnMetadata): ColumnType {
    val declared = column.declaredType.uppercase()
    val type = when (database) {
        CodegenDatabase.SQLITE -> sqliteType(declared.substringBefore('(').trim())
        CodegenDatabase.POSTGRESQL -> when (declared) {
            "BOOL" -> ColumnType.BOOLEAN
            "UUID" -> ColumnType.UUID
            "TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE" -> ColumnType.TIMESTAMP_TZ
            "TEXT", "VARCHAR", "BPCHAR", "NAME" -> ColumnType.STRING
            "BIT", "VARBIT", "TIMETZ" -> null
            else -> if (column.jdbcType == Types.VARCHAR) null else standardJdbcType(column.jdbcType)
        }
        CodegenDatabase.MYSQL -> when {
            declared == "JSON" -> null
            declared == "BIT" && column.size != 1 -> null
            declared == "BIT" -> ColumnType.BOOLEAN
            declared == "INT UNSIGNED" || declared == "INTEGER UNSIGNED" -> ColumnType.LONG
            declared == "BIGINT UNSIGNED" -> ColumnType.BIG_INTEGER
            else -> standardJdbcType(column.jdbcType)
        }
    }
    return requireNotNull(type) { "列 ${column.name} 的类型 ${column.declaredType} 暂不支持" }
}

private fun sqliteType(type: String): ColumnType? = when (type) {
    "BOOL", "BOOLEAN" -> ColumnType.BOOLEAN
    "INTEGER", "INT", "BIGINT", "SMALLINT", "TINYINT", "MEDIUMINT", "INT2", "INT8", "UNSIGNED BIG INT" -> ColumnType.LONG
    "REAL", "DOUBLE", "DOUBLE PRECISION", "FLOAT" -> ColumnType.DOUBLE
    "DECIMAL", "NUMERIC" -> ColumnType.DECIMAL
    "TEXT", "CHAR", "CHARACTER", "VARCHAR", "NCHAR", "NVARCHAR", "CLOB", "VARYING CHARACTER", "NATIVE CHARACTER" ->
        ColumnType.STRING
    "BLOB", "BINARY", "VARBINARY" -> ColumnType.BYTES
    // SQLite 没有原生日期类型，保留已有文本格式和 CURRENT_TIMESTAMP 的表示。
    "DATE", "TIME", "DATETIME", "TIMESTAMP" -> ColumnType.STRING
    else -> null
}

private fun standardJdbcType(type: Int): ColumnType? = when (type) {
    Types.BOOLEAN, Types.BIT -> ColumnType.BOOLEAN
    Types.TINYINT, Types.SMALLINT, Types.INTEGER -> ColumnType.INT
    Types.BIGINT -> ColumnType.LONG
    Types.FLOAT, Types.REAL -> ColumnType.FLOAT
    Types.DOUBLE -> ColumnType.DOUBLE
    Types.DECIMAL, Types.NUMERIC -> ColumnType.DECIMAL
    Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR, Types.CLOB ->
        ColumnType.STRING
    Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> ColumnType.BYTES
    Types.DATE -> ColumnType.DATE
    Types.TIME -> ColumnType.TIME
    Types.TIMESTAMP -> ColumnType.TIMESTAMP
    Types.TIMESTAMP_WITH_TIMEZONE -> ColumnType.TIMESTAMP_TZ
    else -> null
}
