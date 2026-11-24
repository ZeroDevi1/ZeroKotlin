package dev.zerokotlin.codegen

/** JDBC 元数据只参与源代码生成；运行时 SQL 标识符来自生成的白名单。 */
internal fun renderResource(resource: ResourceMetadata): Map<String, String> = with(resource) {
    val source = buildString {
        appendLine("package $packageName")
        appendLine()
        appendLine("import dev.zerokotlin.ktor.ResourceColumn")
        appendLine("import dev.zerokotlin.ktor.ResourceKind")
        appendLine("import dev.zerokotlin.ktor.ResourceSpec")
        appendLine("import dev.zerokotlin.ktor.SystemStore")
        appendLine("import dev.zerokotlin.ktor.generatedResource")
        appendLine("import io.ktor.server.routing.Route")
        appendLine()
        appendLine("internal fun Route.register${name}Routes(store: SystemStore) {")
        appendLine("    generatedResource(")
        appendLine("        store,")
        appendLine("        ResourceSpec(")
        appendLine("            path = ${literal(apiPath.removePrefix("/api/"))},")
        appendLine("            table = ${literal(sqlTable)},")
        appendLine("            mysql = ${database == CodegenDatabase.MYSQL},")
        appendLine("            authority = ${literal(permissions.getValue("read").removeSuffix(":read"))},")
        appendLine("            columns = listOf(")
        for (column in columns) {
            appendLine(
                "                ResourceColumn(${literal(column.property)}, ${literal(database.quote(column.sqlName))}, " +
                    "${literal(column.sqlName)}, ResourceKind.${column.type.name}, ${column.nullable}, " +
                    "${column.identity}, ${column.computed}, ${column.hasDefault}),",
            )
        }
        appendLine("            ),")
        appendLine("        ),")
        appendLine("    )")
        appendLine("}")
    }
    mapOf("src/${packageName.replace('.', '/')}/${name}Routes.kt" to source)
}

private fun literal(value: String): String = buildString {
    append('"')
    for (char in value) when (char) {
        '\\', '"', '$' -> append('\\').append(char)
        else -> append(char)
    }
    append('"')
}
