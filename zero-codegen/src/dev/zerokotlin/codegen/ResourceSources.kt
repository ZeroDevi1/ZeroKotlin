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
        "$sources/service/impl/${name}ServiceImpl.kt" to serviceImplSource(),
        "$sources/controller/${name}Controller.kt" to controllerSource(),
        "$mapperResources/${name}Mapper.xml" to mapperXml(),
    )
}

private fun ResourceMetadata.entitySource(): String = buildString {
    appendLine("package $packageName.entity")
    appendLine()
    if (columns.drop(1).any { it.identity || it.computed || it.nullable }) {
        appendLine("import com.baomidou.mybatisplus.annotation.FieldStrategy")
    }
    appendLine("import com.baomidou.mybatisplus.annotation.IdType")
    if (columns.size > 1) appendLine("import com.baomidou.mybatisplus.annotation.TableField")
    appendLine("import com.baomidou.mybatisplus.annotation.TableId")
    appendLine("import com.baomidou.mybatisplus.annotation.TableName")
    appendLine("import dev.zerokotlin.core.controller.CrudFilter")
    if (columns.size > 1) appendLine("import org.apache.ibatis.type.JdbcType")
    appendLine()
    appendLine("/** 业务表的持久化记录，由 MyBatis-Plus 和显式结果映射加载。 */")
    appendLine("@TableName(value = ${sqlTable.kotlinString()}, resultMap = \"${packageName}.mapper.${name}Mapper.row\")")
    appendLine("class ${name}Record(")
    for (column in columns) {
        val sqlName = database.quote(column.sqlName).kotlinString()
        appendLine("    /** ${column.description.kdocText()} */")
        if (column.type != ColumnType.BYTES) appendLine("    @field:CrudFilter")
        if (column == key) {
            val idType = if (key.identity) "AUTO" else "INPUT"
            appendLine("    @field:TableId(value = $sqlName, type = IdType.$idType)")
        } else {
            val options = buildList {
                add("value = $sqlName")
                add("jdbcType = JdbcType.${column.type.jdbcType}")
                if (column.identity || column.computed) {
                    add("insertStrategy = FieldStrategy.NEVER")
                    add("updateStrategy = FieldStrategy.NEVER")
                } else if (column.nullable) {
                    add("updateStrategy = FieldStrategy.ALWAYS")
                }
            }
            appendLine("    @field:TableField(${options.joinToString()})")
        }
        appendLine("    var ${column.property}: ${column.type.kotlin}? = null,")
    }
    appendLine(")")
}

private fun ResourceMetadata.modelsSource(): String = buildString {
    appendLine("package $packageName.dto")
    appendLine()
    appendLine("import dev.zerokotlin.core.service.ReportColumn")
    appendLine("import io.swagger.v3.oas.annotations.media.Schema")
    appendLine()
    appendModel("${name}Entry", "一条业务记录。", columns, reportColumns = columns.filter { it.type != ColumnType.BYTES })
    appendLine()
    appendLine("/** 创建成功后返回的主键。 */")
    appendLine("@Schema(description = \"创建成功后返回的主键。\")")
    appendLine("data class Created$name(")
    appendLine("    /** 新记录的主键。 */")
    appendLine("    @field:Schema(description = \"新记录的主键。\")")
    appendLine("    val id: ${key.type.kotlin},")
    appendLine(")")
    if (createColumns.isNotEmpty()) {
        appendLine()
        appendModel("Create${name}Request", "创建业务记录的请求。", createColumns, importColumns, ::requiredOnCreate)
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
    reportColumns: List<ResourceColumn> = emptyList(),
    requestRequired: ((ResourceColumn) -> Boolean)? = null,
) {
    appendLine("/** $description */")
    appendLine("@Schema(description = \"$description\")")
    appendLine("data class $typeName(")
    for (column in columns) {
        val required = requestRequired?.invoke(column) == true
        val reportOrder = reportColumns.indexOf(column)
        val mutable = requestRequired != null && reportOrder >= 0
        val modifier = if (mutable) "var" else "val"
        val schema = if (required) ", requiredMode = Schema.RequiredMode.REQUIRED"
        else if (column.nullable || requestRequired != null) ", nullable = true" else ""
        val suffix = when {
            requestRequired != null -> "? = null"
            column.nullable -> "?"
            else -> ""
        }
        appendLine("    /** ${column.description.kdocText()} */")
        appendLine("    @field:Schema(description = ${column.description.kotlinString()}$schema)")
        if (reportOrder >= 0) appendLine("    @field:ReportColumn(name = \"${column.sqlName}\", order = $reportOrder)")
        appendLine("    $modifier ${column.property}: ${column.type.kotlin}$suffix,")
    }
    appendLine(")")
}

private fun ResourceMetadata.mapperSource(): String = buildString {
    appendLine("package $packageName.mapper")
    appendLine()
    appendLine("import com.baomidou.mybatisplus.core.mapper.BaseMapper")
    if (createColumns.isNotEmpty()) appendLine("import $packageName.dto.Create${name}Request")
    if (updateColumns.isNotEmpty()) appendLine("import $packageName.dto.Update${name}Request")
    appendLine("import $packageName.entity.${name}Record")
    appendLine("import org.apache.ibatis.annotations.Mapper")
    if (createColumns.isNotEmpty() || key.identity) appendLine("import org.apache.ibatis.annotations.Param")
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
    appendLine("/** 复用 MyBatis-Plus 的单表操作，通过 XML 定义使用数据库默认值的插入和完整替换。 */")
    appendLine("@Mapper")
    appendLine("interface ${name}Mapper : BaseMapper<${name}Record> {")
    appendLine("    /** 插入一行，返回受影响的行数。 */")
    appendLine("    fun insertRecord(${insertArguments.joinToString()}): Int")
    if (updateColumns.isNotEmpty()) {
        appendLine()
        appendLine("    /** 按主键整体替换可写字段，返回受影响的行数。 */")
        appendLine("    fun replaceRecord(@Param(\"id\") id: ${key.type.kotlin}, @Param(\"request\") request: Update${name}Request): Int")
    }
    appendLine("}")
}

private fun ResourceMetadata.serviceSource(): String = buildString {
    appendLine("package $packageName.service")
    appendLine()
    appendLine("import com.baomidou.mybatisplus.spring.service.IService")
    if (createColumns.isNotEmpty()) appendLine("import $packageName.dto.Create${name}Request")
    appendLine("import $packageName.dto.Created$name")
    if (updateColumns.isNotEmpty()) appendLine("import $packageName.dto.Update${name}Request")
    appendLine("import $packageName.entity.${name}Record")
    appendLine()
    appendLine("/** 提供 $name 的持久化服务与业务写入。 */")
    appendLine("interface ${name}Service : IService<${name}Record> {")
    val createParameter = if (createColumns.isEmpty()) "" else "request: Create${name}Request"
    appendLine("    /** 创建业务记录并返回主键。 */")
    appendLine("    fun create($createParameter): Created$name")
    if (updateColumns.isNotEmpty()) {
        appendLine()
        appendLine("    /** 完整替换业务记录的可写字段。 */")
        appendLine("    fun replace(id: ${key.type.kotlin}, request: Update${name}Request)")
    }
    appendLine("}")
}

private fun ResourceMetadata.serviceImplSource(): String = buildString {
    val id = key.type.kotlin
    val createParameter = if (createColumns.isEmpty()) "" else "request: Create${name}Request"
    val validation = createColumns.filter(::requiredOnCreate).filterNot { it == key }
    appendLine("package $packageName.service.impl")
    appendLine()
    appendLine("import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl")
    appendLine("import dev.zerokotlin.core.OperationAudit")
    if (createColumns.isNotEmpty()) appendLine("import $packageName.dto.Create${name}Request")
    appendLine("import $packageName.dto.Created$name")
    if (updateColumns.isNotEmpty()) appendLine("import $packageName.dto.Update${name}Request")
    appendLine("import $packageName.entity.${name}Record")
    if (key.identity) appendLine("import $packageName.mapper.${name}GeneratedKey")
    appendLine("import $packageName.mapper.${name}Mapper")
    appendLine("import $packageName.service.${name}Service")
    if (updateColumns.isNotEmpty() || validation.isNotEmpty() || !key.identity) {
        appendLine("import org.springframework.http.HttpStatus")
        appendLine("import org.springframework.web.server.ResponseStatusException")
    }
    appendLine("import org.springframework.security.access.prepost.PreAuthorize")
    appendLine("import org.springframework.stereotype.Service")
    appendLine("import org.springframework.transaction.annotation.Transactional")
    appendLine()
    for (operation in listOf("create", "update").filter { it in permissions }) {
        appendLine("private const val ${operation.uppercase()} = \"${permissions.getValue(operation)}\"")
    }
    appendLine()
    appendLine(
        """
        /** 执行业务写入鉴权，在写事务内记录操作审计。 */
        @Service
        class ${name}ServiceImpl(private val audit: OperationAudit) :
            ServiceImpl<${name}Mapper, ${name}Record>(), ${name}Service {
            @PreAuthorize("hasAuthority('" + CREATE + "')")
            @Transactional
            override fun create($createParameter): Created$name {
        """.trimIndent(),
    )
    if (!key.identity) appendLine("        val id = request.${key.property} ?: required(\"${key.property}\")")
    for (column in validation) {
        appendLine("        if (request.${column.property} == null) required(\"${column.property}\")")
    }
    if (key.identity) {
        val arguments = if (createColumns.isEmpty()) "key" else "request, key"
        appendLine("        val key = ${name}GeneratedKey()")
        appendLine("        check(getBaseMapper().insertRecord($arguments) == 1) { \"插入记录失败\" }")
        appendLine("        val id = checkNotNull(key.value) { \"数据库未返回自增主键\" }")
        appendLine("        audit.record(CREATE, id.toString())")
    } else {
        appendLine("        val targetId = ${auditId()}")
        appendLine("        check(getBaseMapper().insertRecord(request) == 1) { \"插入记录失败\" }")
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
                override fun replace(id: $id, request: Update${name}Request) {
                    val targetId = ${auditId()}
            """.trimIndent().prependIndent("    "),
        )
        for (column in updateColumns.filterNot(ResourceColumn::nullable)) {
            appendLine("        if (request.${column.property} == null) required(\"${column.property}\")")
        }
        appendLine("        if (getBaseMapper().replaceRecord(id, request) == 0 && getById(id) == null) missing()")
        appendLine("        audit.record(UPDATE, targetId)")
        appendLine("    }")
    }
    if (updateColumns.isNotEmpty()) {
        appendLine()
        appendLine("    private fun missing(): Nothing =")
        appendLine("        throw ResponseStatusException(HttpStatus.NOT_FOUND, \"记录不存在\")")
    }
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
    appendLine("}")
}

private fun ResourceMetadata.auditId(): String = if (key.type == ColumnType.STRING) "checkedTargetId(id)" else "id.toString()"

private fun ResourceMetadata.controllerSource(): String = buildString {
    val id = key.type.kotlin
    val createParameter = if (createColumns.isEmpty()) "" else "@RequestBody request: Create${name}Request"
    val createArgument = if (createColumns.isEmpty()) "" else "request"
    val reflectedTypes = buildList {
        add("${name}Record")
        add("${name}Entry")
        if (createColumns.isNotEmpty()) add("Create${name}Request")
        if (updateColumns.isNotEmpty()) add("Update${name}Request")
        if (key.identity) add("${name}GeneratedKey")
    }
    appendLine("package $packageName.controller")
    appendLine()
    appendLine("import dev.zerokotlin.core.OperationAudit")
    appendLine("import dev.zerokotlin.core.PageResult")
    appendLine("import dev.zerokotlin.core.PageWindow")
    if (importColumns.isNotEmpty()) appendLine("import dev.zerokotlin.core.controller.CrudImportResult")
    appendLine("import dev.zerokotlin.core.controller.ZeroCrudController")
    appendLine("import dev.zerokotlin.core.service.XLSX_MEDIA_TYPE")
    appendLine("import dev.zerokotlin.core.service.XlsxReport")
    if (createColumns.isNotEmpty()) appendLine("import $packageName.dto.Create${name}Request")
    appendLine("import $packageName.dto.Created$name")
    appendLine("import $packageName.dto.${name}Entry")
    if (updateColumns.isNotEmpty()) appendLine("import $packageName.dto.Update${name}Request")
    appendLine("import $packageName.entity.${name}Record")
    if (key.identity) appendLine("import $packageName.mapper.${name}GeneratedKey")
    appendLine("import $packageName.service.${name}Service")
    appendLine("import io.swagger.v3.oas.annotations.Operation")
    appendLine("import io.swagger.v3.oas.annotations.Parameter")
    appendLine("import io.swagger.v3.oas.annotations.media.Content")
    appendLine("import io.swagger.v3.oas.annotations.media.Schema")
    appendLine("import io.swagger.v3.oas.annotations.responses.ApiResponse")
    appendLine("import io.swagger.v3.oas.annotations.tags.Tag")
    appendLine("import org.springframework.aot.hint.MemberCategory")
    appendLine("import org.springframework.aot.hint.annotation.RegisterReflection")
    appendLine("import org.springframework.http.HttpStatus")
    if (importColumns.isNotEmpty()) appendLine("import org.springframework.http.MediaType")
    appendLine("import org.springframework.http.ResponseEntity")
    appendLine("import org.springframework.security.access.prepost.PreAuthorize")
    appendLine("import org.springframework.transaction.PlatformTransactionManager")
    appendLine("import org.springframework.util.MultiValueMap")
    appendLine("import org.springframework.web.bind.annotation.DeleteMapping")
    appendLine("import org.springframework.web.bind.annotation.GetMapping")
    appendLine("import org.springframework.web.bind.annotation.PathVariable")
    appendLine("import org.springframework.web.bind.annotation.PostMapping")
    if (updateColumns.isNotEmpty()) appendLine("import org.springframework.web.bind.annotation.PutMapping")
    if (createColumns.isNotEmpty()) appendLine("import org.springframework.web.bind.annotation.RequestBody")
    appendLine("import org.springframework.web.bind.annotation.RequestMapping")
    appendLine("import org.springframework.web.bind.annotation.RequestParam")
    if (importColumns.isNotEmpty()) appendLine("import org.springframework.web.bind.annotation.RequestPart")
    appendLine("import org.springframework.web.bind.annotation.ResponseStatus")
    appendLine("import org.springframework.web.bind.annotation.RestController")
    if (importColumns.isNotEmpty()) appendLine("import org.springframework.web.multipart.MultipartFile")
    appendLine()
    for ((access, permission) in permissions) {
        appendLine("private const val ${access.uppercase()} = \"$permission\"")
    }
    appendLine()
    appendLine(
        """
        /** 提供 $name 业务记录的 HTTP 接口。 */
        @RestController
        @RequestMapping("$apiPath")
        @Tag(name = "$name", description = "业务记录管理")
        @RegisterReflection(
            classes = [${reflectedTypes.joinToString { "$it::class" }}],
            memberCategories = [
                MemberCategory.ACCESS_DECLARED_FIELDS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
            ],
        )
        class ${name}Controller(
            private val service: ${name}Service,
            transactions: PlatformTransactionManager,
            audit: OperationAudit,
        ) : ZeroCrudController<${name}Record>(service, ${name}Record::class.java, transactions, audit) {
            private val report = XlsxReport(${name}Entry::class.java)
        """.trimIndent(),
    )
    if (importColumns.isNotEmpty()) {
        appendLine("    private val importReport = XlsxReport(Create${name}Request::class.java)")
    }
    appendLine()
    appendLine(
        """
            /** 按允许的字段作等值筛选并分页。 */
            @GetMapping
            @PreAuthorize("hasAuthority('" + READ + "')")
            ${operation("read", "分页查询业务记录，可通过 filter.<属性名> 等值筛选", "200", hasBody = true)}
            fun list(
                @Parameter(description = "页码，从 1 开始")
                @RequestParam(name = "page", defaultValue = "1") page: Int,
                @Parameter(description = "每页条数，范围为 1～100")
                @RequestParam(name = "size", defaultValue = "20") size: Int,
                @Parameter(hidden = true) @RequestParam parameters: MultiValueMap<String, String>,
            ): PageResult<${name}Entry> {
                val window = PageWindow(page, size)
                val result = queryPage(window, parameters)
                return window.result(result.items.map { it.toEntry() }, result.total)
            }

            /** 按主键读取单条记录。 */
            @GetMapping("/{id}")
            @PreAuthorize("hasAuthority('" + READ + "')")
            ${operation("read", "按主键查询业务记录", "200", hasBody = true)}
            fun get(@PathVariable("id") id: $id): ${name}Entry = queryById(id).toEntry()

            /** 创建业务记录并返回主键。 */
            @PostMapping
            @PreAuthorize("hasAuthority('" + CREATE + "')")
            @ResponseStatus(HttpStatus.CREATED)
            ${operation("create", "创建业务记录", "201", hasBody = true)}
            fun create($createParameter): Created$name = service.create($createArgument)
        """.trimIndent().prependIndent("    "),
    )
    if (updateColumns.isNotEmpty()) {
        appendLine()
        appendLine(
            """
                /** 全量替换业务记录的可写字段。 */
                @PutMapping("/{id}")
                @PreAuthorize("hasAuthority('" + UPDATE + "')")
                @ResponseStatus(HttpStatus.NO_CONTENT)
                ${operation("update", "替换业务记录的可写字段", "204", hasBody = false)}
                fun update(@PathVariable("id") id: $id, @RequestBody request: Update${name}Request) =
                    service.replace(id, request)
            """.trimIndent().prependIndent("    "),
        )
    }
    appendLine()
    appendLine(
        """
            /** 按主键物理删除业务记录。 */
            @DeleteMapping("/{id}")
            @PreAuthorize("hasAuthority('" + DELETE + "')")
            @ResponseStatus(HttpStatus.NO_CONTENT)
            ${operation("delete", "删除业务记录", "204", hasBody = false)}
            fun delete(@PathVariable("id") id: $id) = delete(id, DELETE)

            /** 导出当前筛选条件下的报表。 */
            @GetMapping("/excel/export", produces = [XLSX_MEDIA_TYPE])
            @PreAuthorize("hasAuthority('" + READ + "')")
            @Operation(summary = "导出业务报表", description = "需要 ${permissions.getValue("read")} 权限，沿用列表筛选条件，最多导出 1000 条。")
            @ApiResponse(responseCode = "200", description = "XLSX 文件",
                content = [Content(mediaType = XLSX_MEDIA_TYPE, schema = Schema(type = "string", format = "binary"))])
            fun export(
                @Parameter(hidden = true) @RequestParam parameters: MultiValueMap<String, String>,
            ): ResponseEntity<ByteArray> = exportXls("$name.xlsx", parameters, report) { it.toEntry() }
        """.trimIndent().prependIndent("    "),
    )
    if (importColumns.isNotEmpty()) {
        appendLine()
        appendLine(
            """
                /** 下载批量创建所用的空白模板。 */
                @GetMapping("/excel/template", produces = [XLSX_MEDIA_TYPE])
                @PreAuthorize("hasAuthority('" + CREATE + "')")
                @Operation(summary = "下载 XLSX 导入模板", description = "需要 ${permissions.getValue("create")} 权限。")
                @ApiResponse(responseCode = "200", description = "XLSX 文件",
                    content = [Content(mediaType = XLSX_MEDIA_TYPE, schema = Schema(type = "string", format = "binary"))])
                fun template(): ResponseEntity<ByteArray> = templateXls("${name}导入模板.xlsx", importReport)

                /** 按业务创建规则批量新增记录。 */
                @PostMapping("/excel/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
                @PreAuthorize("hasAuthority('" + CREATE + "')")
                @ResponseStatus(HttpStatus.CREATED)
                ${operation("create", "通过 XLSX 批量新增记录，整批提交", "201", hasBody = true)}
                fun importExcel(@RequestPart("file") file: MultipartFile): CrudImportResult =
                    importXls(file, importReport, { Create${name}Request() }) { service.create(it) }
            """.trimIndent().prependIndent("    "),
        )
    }
    appendLine()
    appendLine("    private fun ${name}Record.toEntry() = ${name}Entry(")
    for (column in columns) {
        val value = "this.${column.property}"
        appendLine("        ${column.property} = ${if (column.nullable) value else "checkNotNull($value)"},")
    }
    appendLine("    )")
    appendLine("}")
}

private fun ResourceMetadata.operation(access: String, summary: String, status: String, hasBody: Boolean): String {
    val permission = permissions.getValue(access)
    val schema = if (hasBody) ", useReturnTypeSchema = true" else ""
    return "@Operation(summary = \"$summary\", description = \"需要 $permission 权限。\", " +
        "responses = [ApiResponse(responseCode = \"$status\", description = \"操作成功\"$schema)])"
}

private fun String.kdocText(): String = replace("/*", "/ *").replace("*/", "* /")

private fun String.kotlinString(): String = buildString {
    append('"')
    for (character in this@kotlinString) {
        if (character in listOf('\\', '"', '$')) append('\\')
        append(character)
    }
    append('"')
}
