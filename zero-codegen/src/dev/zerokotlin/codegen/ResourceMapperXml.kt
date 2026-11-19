package dev.zerokotlin.codegen

/** 生成底层列映射及有数据库默认值、完整替换语义的参数化写入。 */
internal fun ResourceMetadata.mapperXml(): String = buildString {
    val keyName = database.quote(key.sqlName)
    val keyFilter = "$keyName = ${key.parameter("id")}"
    appendLine(
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
        <mapper namespace="$packageName.mapper.${name}Mapper">
            <resultMap id="row" type="$packageName.entity.${name}Record" autoMapping="false">
        """.trimIndent(),
    )
    for (column in columns) {
        val tag = if (column == key) "id" else "result"
        appendLine("        <$tag column=\"${column.sqlName}\" property=\"${column.property}\"/>")
    }
    appendLine("    </resultMap>")
    appendLine()
    appendInsert(this)
    if (updateColumns.isNotEmpty()) {
        appendLine()
        appendLine("    <update id=\"replaceRecord\">")
        appendLine("        UPDATE $sqlTable SET")
        for ((index, column) in updateColumns.withIndex()) {
            val separator = if (index == updateColumns.lastIndex) "" else ","
            appendLine("            ${database.quote(column.sqlName)} = ${column.parameter("request.${column.property}")}$separator")
        }
        appendLine("        WHERE $keyFilter")
        appendLine("    </update>")
    }
    appendLine("</mapper>")
}

private fun ResourceMetadata.appendInsert(xml: StringBuilder) {
    val generatedKey = if (key.identity) " useGeneratedKeys=\"true\" keyProperty=\"key.value\"" else ""
    val keyColumn = if (key.identity && database == CodegenDatabase.POSTGRESQL) " keyColumn=\"${key.sqlName}\"" else ""
    val defaultValues = if (database == CodegenDatabase.MYSQL) "() VALUES ()" else "DEFAULT VALUES"
    xml.appendLine("    <insert id=\"insertRecord\"$generatedKey$keyColumn>")
    xml.appendLine("        INSERT INTO $sqlTable")
    when {
        createColumns.isEmpty() -> xml.appendLine("        $defaultValues")
        createColumns.none(::useDefaultOnCreate) -> {
            xml.appendLine("        (${createColumns.joinToString { database.quote(it.sqlName) }})")
            xml.appendLine("        VALUES (${createColumns.joinToString { it.parameter("request.${it.property}") }})")
        }
        createColumns.all(::useDefaultOnCreate) -> {
            val anySpecified = createColumns.joinToString(" or ") { "request.${it.property} != null" }
            xml.appendLine("        <choose>")
            xml.appendLine("            <when test=\"$anySpecified\">")
            appendDynamicInsert(xml, "                ")
            xml.appendLine("            </when>")
            xml.appendLine("            <otherwise>$defaultValues</otherwise>")
            xml.appendLine("        </choose>")
        }
        else -> appendDynamicInsert(xml, "        ")
    }
    xml.appendLine("    </insert>")
}

private fun ResourceMetadata.appendDynamicInsert(xml: StringBuilder, indent: String) {
    val names = createColumns.map { database.quote(it.sqlName) }
    val values = createColumns.map { it.parameter("request.${it.property}") }
    for ((prefix, parts) in listOf("(" to names, "VALUES (" to values)) {
        xml.appendLine("$indent<trim prefix=\"$prefix\" suffix=\")\" suffixOverrides=\",\">")
        for ((column, part) in createColumns.zip(parts)) {
            if (useDefaultOnCreate(column)) {
                xml.appendLine("$indent    <if test=\"request.${column.property} != null\">$part,</if>")
            } else {
                xml.appendLine("$indent    $part,")
            }
        }
        xml.appendLine("$indent</trim>")
    }
}

private fun ResourceColumn.parameter(property: String): String = "#{$property,jdbcType=${type.jdbcType}}"
