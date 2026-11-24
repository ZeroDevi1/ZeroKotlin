package dev.zerokotlin.codegen

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties
import kotlin.system.exitProcess

private val usage = """
    用法：./gradlew :zero-codegen:run --args='--module zero-system --package dev.zerokotlin.orders --table orders_order [选项]'
      --jdbc-url <url>     JDBC URL，省略时读取 ZERO_CODEGEN_JDBC_URL
      --schema <name>     PostgreSQL schema，默认为当前 schema
      --catalog <name>    MySQL catalog，默认为当前数据库
      --name <PascalCase> 覆盖生成的资源类名词干，默认按表名转换
      --dry-run           读取元数据并检查目标路径，只列出拟生成文件
      --help              显示帮助
    远程数据库凭据从 ZERO_CODEGEN_DB_USERNAME 和 ZERO_CODEGEN_DB_PASSWORD 读取。
""".trimIndent()

/** 从指定数据库读取表定义，在现有业务模块中生成 Ktor Controller、Service 和 MyBatis-Plus Mapper。 */
fun main(args: Array<String>) {
    if (args.isEmpty() || args.contentEquals(arrayOf("--help"))) {
        println(usage)
        return
    }
    val options = try {
        parseOptions(args)
    } catch (error: IllegalArgumentException) {
        System.err.println("参数错误：${error.message}")
        System.err.println(usage)
        exitProcess(2)
    }
    try {
        val database = options.database
        val url = if (database == CodegenDatabase.SQLITE) sqliteReadOnlyUrl(options.url) else options.url
        val properties = Properties()
        if (database != CodegenDatabase.SQLITE) {
            System.getenv("ZERO_CODEGEN_DB_USERNAME")?.let { properties.setProperty("user", it) }
            System.getenv("ZERO_CODEGEN_DB_PASSWORD")?.let { properties.setProperty("password", it) }
        }
        val model = DriverManager.getConnection(url, properties).use { connection ->
            readResource(connection, options)
        }
        val output = prepareOutput(options.module, renderResource(model))
        if (!options.dryRun) writeOutput(output)
        println(if (options.dryRun) "拟生成文件" else "已生成文件")
        output.keys.forEach(::println)
        println("接口路径 ${model.apiPath}")
        println("权限编码 ${model.permissions.values.joinToString(", ")}")
    } catch (error: SQLException) {
        // JDBC 异常正文可能包含连接串或凭据。
        fail("数据库连接或元数据查询失败（SQLState=${error.sqlState ?: "未知"}，错误码=${error.errorCode}）")
    } catch (error: IOException) {
        val rollback = if (error.suppressed.isEmpty()) "" else "。清理未完成，请检查本次的目标文件"
        fail("文件操作失败：${error.message}$rollback")
    } catch (error: IllegalArgumentException) {
        fail(error.message.orEmpty())
    }
}

private fun fail(message: String): Nothing {
    System.err.println("生成失败：$message")
    exitProcess(1)
}

/** 解析后的单表生成请求。 */
internal data class CodegenOptions(
    val module: Path,
    val packageName: String,
    val table: String,
    val classStem: String?,
    val database: CodegenDatabase,
    val url: String,
    val schema: String?,
    val catalog: String?,
    val dryRun: Boolean,
)

private fun parseOptions(args: Array<String>): CodegenOptions {
    val values = mutableMapOf<String, String>()
    val valuedOptions = setOf("--module", "--package", "--table", "--jdbc-url", "--schema", "--catalog", "--name")
    var dryRun = false
    var index = 0
    while (index < args.size) {
        val flag = args[index++]
        if (flag == "--dry-run") {
            require(!dryRun) { "选项 --dry-run 重复" }
            dryRun = true
            continue
        }
        require(flag in valuedOptions) { "存在未知选项，请使用 --help 查看用法" }
        require(flag !in values) { "选项 $flag 重复" }
        val value = args.getOrNull(index++)
        require(!value.isNullOrBlank() && !value.startsWith("--")) { "$flag 需要一个值" }
        values[flag] = value
    }
    fun required(flag: String): String = requireNotNull(values[flag]) { "缺少 $flag" }

    val url = requireNotNull(values["--jdbc-url"] ?: System.getenv("ZERO_CODEGEN_JDBC_URL")?.takeIf(String::isNotBlank)) {
        "请提供 --jdbc-url 或设置 ZERO_CODEGEN_JDBC_URL"
    }
    val database = CodegenDatabase.entries.firstOrNull { url.startsWith(it.urlPrefix) }
        ?: throw IllegalArgumentException("JDBC URL 须以 jdbc:sqlite:、jdbc:postgresql:// 或 jdbc:mysql:// 开头")
    val schema = values["--schema"]?.let { checkedSqlName(it, "schema") }
    val catalog = values["--catalog"]?.let { checkedSqlName(it, "catalog") }
    require(schema == null || database == CodegenDatabase.POSTGRESQL) { "--schema 仅用于 PostgreSQL" }
    require(catalog == null || database == CodegenDatabase.MYSQL) { "--catalog 仅用于 MySQL" }
    return CodegenOptions(
        Path.of(required("--module")),
        checkedPackage(required("--package")),
        checkedSqlName(required("--table"), "表名"),
        values["--name"]?.also {
            require(it.matches(Regex("[A-Z][A-Za-z0-9]*"))) { "--name 须使用 PascalCase 标识符" }
        },
        database, url, schema, catalog, dryRun,
    )
}

private fun sqliteReadOnlyUrl(url: String): String {
    val location = url.removePrefix(CodegenDatabase.SQLITE.urlPrefix)
    require(location.isNotBlank() && !location.startsWith("file:") && location != ":memory:" && '?' !in location) {
        "SQLite JDBC URL 须指向已有本地文件，格式为 jdbc:sqlite:<路径>"
    }
    val file = Path.of(location).toAbsolutePath().normalize()
    require(Files.isRegularFile(file)) { "SQLite 文件不存在或不是普通文件：$file" }
    return "jdbc:sqlite:${file.toUri()}?mode=ro"
}

private fun prepareOutput(module: Path, generated: Map<String, String>): Map<Path, String> {
    require(Files.isDirectory(module) && Files.isRegularFile(module.resolve("build.gradle.kts"))) {
        "--module 须指向包含 build.gradle.kts 的现有 Gradle 模块：$module"
    }
    val root = module.toRealPath()
    val files = generated.mapKeys { (relative, _) -> root.resolve(relative) }
    for (path in files.keys) {
        var directory = path.parent
        while (directory != root) {
            require(!Files.exists(directory, NOFOLLOW_LINKS) || Files.isDirectory(directory, NOFOLLOW_LINKS)) {
                "目标目录包含符号链接或非目录路径：$directory"
            }
            directory = directory.parent
        }
    }
    val conflicts = files.keys.filter { Files.exists(it, NOFOLLOW_LINKS) }
    require(conflicts.isEmpty()) { "以下目标已存在，已取消生成：\n${conflicts.joinToString("\n")}" }
    return files
}

private fun writeOutput(files: Map<Path, String>) {
    val created = mutableListOf<Path>()
    try {
        for ((path, content) in files) {
            Files.createDirectories(path.parent)
            Files.newBufferedWriter(path, Charsets.UTF_8, CREATE_NEW, WRITE).use { writer ->
                created.add(path)
                writer.write(content)
            }
        }
    } catch (error: IOException) {
        for (path in created.asReversed()) {
            try {
                Files.deleteIfExists(path)
            } catch (cleanup: IOException) {
                error.addSuppressed(cleanup)
            }
        }
        throw error
    }
}
