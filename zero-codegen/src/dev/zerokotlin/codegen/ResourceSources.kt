package dev.zerokotlin.codegen

/** 为目标模块生成职责包及 Mapper XML，文件内容在落盘检查前全部组装。 */
internal fun renderResource(resource: ResourceMetadata): Map<String, String> = with(resource) {
    val sources = "src/${packageName.replace('.', '/')}"
    val mapperResources = "resources/mapper/${namespace.replace('.', '/')}"
    mapOf(
        "$sources/entity/${name}Record.kt" to entitySource(),
        "$sources/dto/${name}Models.kt" to modelsSource(),
        "$sources/mapper/${name}Mapper.kt" to mapperSource(),
        "$sources/service/${name}Service.kt" to serviceSource(),
        "$sources/controller/${name}Controller.kt" to controllerSource(),
        "$mapperResources/${name}Mapper.xml" to mapperXml(),
    )
}

private fun ResourceMetadata.entitySource(): String = buildString {
    appendLine("package $packageName.entity")
    appendLine()
    appendLine("/** 业务表的持久化记录，由 Mapper XML 显式按列映射。 */")
    appendLine("class ${name}Record(")
    for (column in columns) {
        val suffix = if (column.nullable) "?" else ""
        appendLine("    val ${column.property}: ${column.type.kotlin}$suffix,")
    }
    appendLine(")")
}

private fun ResourceMetadata.modelsSource(): String = buildString {
    appendLine("package $packageName.dto")
    appendLine()
    appendLine("import io.swagger.v3.oas.annotations.media.Schema")
    appendLine()
    appendModel("${name}Entry", "一条业务记录。", columns)
    appendLine()
    appendLine("/** 创建成功后返回的主键。 */")
    appendLine("@Schema(description = \"创建成功后返回的主键。\")")
    appendLine("data class Created$name(")
    appendLine("    @field:Schema(description = \"新记录的主键。\")")
    appendLine("    val id: ${key.type.kotlin},")
    appendLine(")")
    if (createColumns.isNotEmpty()) {
        appendLine()
        appendModel("Create${name}Request", "创建业务记录的请求。", createColumns, ::requiredOnCreate)
    }
    if (updateColumns.isNotEmpty()) {
        appendLine()
        appendModel("Update${name}Request", "完整替换可写字段的请求。", updateColumns) { !it.nullable }
    }
}

private fun StringBuilder.appendModel(
    typeName: String,
    description: String,
    columns: List<ResourceColumn>,
    requestRequired: ((ResourceColumn) -> Boolean)? = null,
) {
    appendLine("/** $description */")
    appendLine("@Schema(description = \"$description\")")
    appendLine("data class $typeName(")
    for (column in columns) {
        val required = requestRequired?.invoke(column) == true
        val detail = "数据库列 ${column.sqlName}。"
        val schema = if (required) ", requiredMode = Schema.RequiredMode.REQUIRED"
        else if (column.nullable || requestRequired != null) ", nullable = true" else ""
        val suffix = when {
            requestRequired != null -> "? = null"
            column.nullable -> "?"
            else -> ""
        }
        appendLine("    @field:Schema(description = \"$detail\"$schema)")
        appendLine("    val ${column.property}: ${column.type.kotlin}$suffix,")
    }
    appendLine(")")
}

private fun ResourceMetadata.mapperSource(): String = buildString {
    appendLine("package $packageName.mapper")
    appendLine()
    appendLine("import dev.zerokotlin.core.PageWindow")
    if (createColumns.isNotEmpty()) appendLine("import $packageName.dto.Create${name}Request")
    if (updateColumns.isNotEmpty()) appendLine("import $packageName.dto.Update${name}Request")
    appendLine("import $packageName.entity.${name}Record")
    appendLine("import org.apache.ibatis.annotations.Mapper")
    appendLine("import org.apache.ibatis.annotations.Param")
    appendLine()
    if (key.identity) {
        appendLine("/** 接收 JDBC 返回的数据库自增主键。 */")
        appendLine("class ${name}GeneratedKey(var value: ${key.type.kotlin}? = null)")
        appendLine()
    }
    val insertArguments = buildList {
        if (createColumns.isNotEmpty()) add("@Param(\"request\") request: Create${name}Request")
        if (key.identity) add("@Param(\"key\") key: ${name}GeneratedKey")
    }
    appendLine("/** 由同名 XML 定义业务表的分页查询和单行读写。 */")
    appendLine("@Mapper")
    appendLine("interface ${name}Mapper {")
    appendLine("    /** 统计分页查询的记录总数。 */")
    appendLine("    fun count(): Long")
    appendLine()
    appendLine("    /** 按主键升序读取一页。 */")
    appendLine("    fun list(@Param(\"window\") window: PageWindow): List<${name}Record>")
    appendLine()
    appendLine("    /** 按主键读取一行。 */")
    appendLine("    fun findById(@Param(\"id\") id: ${key.type.kotlin}): ${name}Record?")
    appendLine()
    appendLine("    /** 插入一行，返回受影响的行数。 */")
    appendLine("    fun insert(${insertArguments.joinToString()}): Int")
    if (updateColumns.isNotEmpty()) {
        appendLine()
        appendLine("    /** 按主键整体替换可写字段，返回受影响的行数。 */")
        appendLine("    fun update(@Param(\"id\") id: ${key.type.kotlin}, @Param(\"request\") request: Update${name}Request): Int")
    }
    appendLine()
    appendLine("    /** 按主键物理删除一行，返回受影响的行数。 */")
    appendLine("    fun delete(@Param(\"id\") id: ${key.type.kotlin}): Int")
    appendLine("}")
}

private fun ResourceMetadata.serviceSource(): String = buildString {
    val id = key.type.kotlin
    val createParameter = if (createColumns.isEmpty()) "" else "request: Create${name}Request"
    val validation = createColumns.filter(::requiredOnCreate).filterNot { it == key }
    appendLine("package $packageName.service")
    appendLine()
    appendLine("import dev.zerokotlin.core.OperationAudit")
    appendLine("import dev.zerokotlin.core.PageResult")
    appendLine("import dev.zerokotlin.core.PageWindow")
    if (createColumns.isNotEmpty()) appendLine("import $packageName.dto.Create${name}Request")
    appendLine("import $packageName.dto.Created$name")
    appendLine("import $packageName.dto.${name}Entry")
    if (updateColumns.isNotEmpty()) appendLine("import $packageName.dto.Update${name}Request")
    appendLine("import $packageName.entity.${name}Record")
    if (key.identity) appendLine("import $packageName.mapper.${name}GeneratedKey")
    appendLine("import $packageName.mapper.${name}Mapper")
    appendLine("import org.springframework.http.HttpStatus")
    appendLine("import org.springframework.security.access.prepost.PreAuthorize")
    appendLine("import org.springframework.stereotype.Service")
    appendLine("import org.springframework.transaction.annotation.Transactional")
    appendLine("import org.springframework.web.server.ResponseStatusException")
    appendLine()
    for ((operation, permission) in permissions) {
        appendLine("private const val ${operation.uppercase()} = \"$permission\"")
    }
    appendLine()
    appendLine(
        """
        /** 执行业务表的读写鉴权，并在写事务内记录操作审计。 */
        @Service
        class ${name}Service(
            private val mapper: ${name}Mapper,
            private val audit: OperationAudit,
        ) {
            /** 按主键升序查询业务记录。 */
            @PreAuthorize("hasAuthority('" + READ + "')")
            @Transactional(readOnly = true)
            fun list(window: PageWindow): PageResult<${name}Entry> =
                window.result(mapper.list(window).map { it.toEntry() }, mapper.count())

            /** 按主键查询单条记录，找不到时返回 HTTP 404。 */
            @PreAuthorize("hasAuthority('" + READ + "')")
            @Transactional(readOnly = true)
            fun get(id: $id): ${name}Entry = (mapper.findById(id) ?: missing()).toEntry()

            /** 校验创建字段，写入记录与审计并返回主键。 */
            @PreAuthorize("hasAuthority('" + CREATE + "')")
            @Transactional
            fun create($createParameter): Created$name {
        """.trimIndent(),
    )
    if (!key.identity) appendLine("        val id = request.${key.property} ?: required(\"${key.property}\")")
    for (column in validation) {
        appendLine("        if (request.${column.property} == null) required(\"${column.property}\")")
    }
    if (key.identity) {
        val arguments = if (createColumns.isEmpty()) "key" else "request, key"
        appendLine("        val key = ${name}GeneratedKey()")
        appendLine("        check(mapper.insert($arguments) == 1) { \"插入记录失败\" }")
        appendLine("        val id = checkNotNull(key.value) { \"数据库未返回自增主键\" }")
        appendLine("        audit.record(CREATE, id.toString())")
    } else {
        appendLine("        val targetId = ${auditId()}")
        appendLine("        check(mapper.insert(request) == 1) { \"插入记录失败\" }")
        appendLine("        audit.record(CREATE, targetId)")
    }
    appendLine("        return Created$name(id)")
    appendLine("    }")
    if (updateColumns.isNotEmpty()) {
        appendLine()
        appendLine(
            """
                /** 替换可写字段，并在同一事务记录操作审计。 */
                @PreAuthorize("hasAuthority('" + UPDATE + "')")
                @Transactional
                fun update(id: $id, request: Update${name}Request) {
                    val targetId = ${auditId()}
            """.trimIndent().prependIndent("    "),
        )
        for (column in updateColumns.filterNot(ResourceColumn::nullable)) {
            appendLine("        if (request.${column.property} == null) required(\"${column.property}\")")
        }
        appendLine("        if (mapper.update(id, request) == 0 && mapper.findById(id) == null) missing()")
        appendLine("        audit.record(UPDATE, targetId)")
        appendLine("    }")
    }
    appendLine()
    appendLine(
        """
            /** 按主键物理删除记录，并在同一事务记录操作审计。 */
            @PreAuthorize("hasAuthority('" + DELETE + "')")
            @Transactional
            fun delete(id: $id) {
                val targetId = ${auditId()}
                if (mapper.delete(id) == 0) missing()
                audit.record(DELETE, targetId)
            }

            private fun missing(): Nothing =
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在")
        """.trimIndent().prependIndent("    "),
    )
    if (!key.identity || validation.isNotEmpty() || updateColumns.any { !it.nullable }) {
        appendLine()
        appendLine("    private fun required(field: String): Nothing =")
        appendLine("        throw ResponseStatusException(HttpStatus.BAD_REQUEST, \"${'$'}field 为必填项\")")
    }
    if (key.type == ColumnType.STRING) {
        appendLine()
        appendLine(
            """
                private fun checkedTargetId(id: String): String {
                    if (id.isBlank() || id.length > 120 || id == "." || id == ".." ||
                        id.any { it.isISOControl() || it in "/\\;%" }
                    ) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "主键须为长度不超过 120 的合法路径段")
                    }
                    return id
                }
            """.trimIndent().prependIndent("    "),
        )
    }
    appendLine()
    appendLine("    private fun ${name}Record.toEntry() = ${name}Entry(")
    for (column in columns) appendLine("        ${column.property} = this.${column.property},")
    appendLine("    )")
    appendLine("}")
}

private fun ResourceMetadata.auditId(): String = if (key.type == ColumnType.STRING) "checkedTargetId(id)" else "id.toString()"

private fun ResourceMetadata.controllerSource(): String = buildString {
    val id = key.type.kotlin
    val createParameter = if (createColumns.isEmpty()) "" else "@RequestBody request: Create${name}Request"
    val createArgument = if (createColumns.isEmpty()) "" else "request"
    appendLine("package $packageName.controller")
    appendLine()
    appendLine("import dev.zerokotlin.core.PageResult")
    appendLine("import dev.zerokotlin.core.PageWindow")
    if (createColumns.isNotEmpty()) appendLine("import $packageName.dto.Create${name}Request")
    appendLine("import $packageName.dto.Created$name")
    appendLine("import $packageName.dto.${name}Entry")
    if (updateColumns.isNotEmpty()) appendLine("import $packageName.dto.Update${name}Request")
    appendLine("import $packageName.service.${name}Service")
    appendLine("import io.swagger.v3.oas.annotations.Operation")
    appendLine("import io.swagger.v3.oas.annotations.Parameter")
    appendLine("import io.swagger.v3.oas.annotations.responses.ApiResponse")
    appendLine("import io.swagger.v3.oas.annotations.tags.Tag")
    appendLine("import org.springframework.http.HttpStatus")
    appendLine("import org.springframework.web.bind.annotation.DeleteMapping")
    appendLine("import org.springframework.web.bind.annotation.GetMapping")
    appendLine("import org.springframework.web.bind.annotation.PathVariable")
    appendLine("import org.springframework.web.bind.annotation.PostMapping")
    if (updateColumns.isNotEmpty()) appendLine("import org.springframework.web.bind.annotation.PutMapping")
    if (createColumns.isNotEmpty()) appendLine("import org.springframework.web.bind.annotation.RequestBody")
    appendLine("import org.springframework.web.bind.annotation.RequestMapping")
    appendLine("import org.springframework.web.bind.annotation.RequestParam")
    appendLine("import org.springframework.web.bind.annotation.ResponseStatus")
    appendLine("import org.springframework.web.bind.annotation.RestController")
    appendLine()
    appendLine(
        """
        /** 提供 $name 业务记录的 HTTP 接口。 */
        @RestController
        @RequestMapping("$apiPath")
        @Tag(name = "$name", description = "业务记录管理")
        class ${name}Controller(private val service: ${name}Service) {
            /** 分页查询业务记录。 */
            @GetMapping
            ${operation("read", "分页查询业务记录", "200", hasBody = true)}
            fun list(
                @Parameter(description = "页码，从 1 开始")
                @RequestParam(name = "page", defaultValue = "1") page: Int,
                @Parameter(description = "每页条数，范围为 1～100")
                @RequestParam(name = "size", defaultValue = "20") size: Int,
            ): PageResult<${name}Entry> = service.list(PageWindow(page, size))

            /** 按主键读取业务记录。 */
            @GetMapping("/{id}")
            ${operation("read", "按主键查询业务记录", "200", hasBody = true)}
            fun get(@PathVariable("id") id: $id): ${name}Entry = service.get(id)

            /** 创建业务记录并返回主键。 */
            @PostMapping
            @ResponseStatus(HttpStatus.CREATED)
            ${operation("create", "创建业务记录", "201", hasBody = true)}
            fun create($createParameter): Created$name = service.create($createArgument)
        """.trimIndent(),
    )
    if (updateColumns.isNotEmpty()) {
        appendLine()
        appendLine(
            """
                /** 全量替换业务记录的可写字段。 */
                @PutMapping("/{id}")
                @ResponseStatus(HttpStatus.NO_CONTENT)
                ${operation("update", "替换业务记录的可写字段", "204", hasBody = false)}
                fun update(@PathVariable("id") id: $id, @RequestBody request: Update${name}Request) =
                    service.update(id, request)
            """.trimIndent().prependIndent("    "),
        )
    }
    appendLine()
    appendLine(
        """
            /** 按主键物理删除业务记录。 */
            @DeleteMapping("/{id}")
            @ResponseStatus(HttpStatus.NO_CONTENT)
            ${operation("delete", "删除业务记录", "204", hasBody = false)}
            fun delete(@PathVariable("id") id: $id) = service.delete(id)
        """.trimIndent().prependIndent("    "),
    )
    appendLine("}")
}

private fun ResourceMetadata.operation(access: String, summary: String, status: String, hasBody: Boolean): String {
    val permission = permissions.getValue(access)
    val schema = if (hasBody) ", useReturnTypeSchema = true" else ""
    return "@Operation(summary = \"$summary\", description = \"需要 $permission 权限。\", " +
        "responses = [ApiResponse(responseCode = \"$status\", description = \"操作成功\"$schema)])"
}
