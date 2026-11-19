package dev.zerokotlin.codegen

/** 生成显式列映射、分页与参数化写入。标识符来自已验证的元数据，运行时值全部绑定参数。 */
internal fun ResourceMetadata.mapperXml(): String = buildString {
    val keyName = database.quote(key.sqlName)
    val keyFilter = "$keyName = ${key.parameter("id")}"
    appendLine(
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
        <mapper namespace="$packageName.mapper.${name}Mapper">
            <resultMap id="row" type="$packageName.entity.${name}Record" autoMapping="false">
                <constructor>
        """.trimIndent(),
    )
    for (column in columns) {
        val tag = if (column == key) "idArg" else "arg"
        val javaType = if (!column.nullable && column.type.primitive) "_${column.type.javaType}" else column.type.javaType
        val handler = column.type.handler?.let { " typeHandler=\"$it\"" }.orEmpty()
        appendLine("            <$tag column=\"${column.sqlName}\" javaType=\"$javaType\"$handler/>")
    }
    appendLine("        </constructor>")
    appendLine("    </resultMap>")
    appendLine()
    appendLine("    <sql id=\"columns\">${columns.joinToString { database.quote(it.sqlName) }}</sql>")
    appendLine()
    appendLine(
        """
            <select id="count" resultType="long">
                SELECT COUNT(*) FROM $sqlTable
            </select>

            <select id="list" resultMap="row">
                SELECT <include refid="columns"/> FROM $sqlTable
                ORDER BY $keyName
                LIMIT #{window.size} OFFSET #{window.offset}
            </select>

            <select id="findById" resultMap="row">
                SELECT <include refid="columns"/> FROM $sqlTable WHERE $keyFilter
            </select>
        """.trimIndent().prependIndent("    "),
    )
    appendLine()
    appendInsert(this)
    if (updateColumns.isNotEmpty()) {
        appendLine()
        appendLine("    <update id=\"update\">")
        appendLine("        UPDATE $sqlTable SET")
        for ((index, column) in updateColumns.withIndex()) {
            val separator = if (index == updateColumns.lastIndex) "" else ","
            appendLine("            ${database.quote(column.sqlName)} = ${column.parameter("request.${column.property}")}$separator")
        }
        appendLine("        WHERE $keyFilter")
        appendLine("    </update>")
    }
    appendLine()
    appendLine("    <delete id=\"delete\">")
    appendLine("        DELETE FROM $sqlTable WHERE $keyFilter")
    appendLine("    </delete>")
    appendLine("</mapper>")
}

private fun ResourceMetadata.appendInsert(xml: StringBuilder) {
    val generatedKey = if (key.identity) " useGeneratedKeys=\"true\" keyProperty=\"key.value\"" else ""
    val keyColumn = if (key.identity && database == CodegenDatabase.POSTGRESQL) " keyColumn=\"${key.sqlName}\"" else ""
    val defaultValues = if (database == CodegenDatabase.MYSQL) "() VALUES ()" else "DEFAULT VALUES"
    xml.appendLine("    <insert id=\"insert\"$generatedKey$keyColumn>")
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

private fun ResourceColumn.parameter(property: String): String {
    val handler = type.handler?.let { ",typeHandler=$it" }.orEmpty()
    return "#{$property,jdbcType=${type.jdbcType}$handler}"
}
