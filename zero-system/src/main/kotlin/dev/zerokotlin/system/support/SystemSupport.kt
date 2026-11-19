package dev.zerokotlin.system.support

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import java.util.Locale
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private val usernamePattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{2,63}")

private val codePattern = Regex("[a-z0-9][a-z0-9:_-]{0,119}")

fun newId(): String = UUID.randomUUID().toString()

fun Boolean.flag(): Int = if (this) 1 else 0

fun Boolean?.requiredFlag(): Int = this?.flag() ?: invalid("enabled 为必填项")

fun normalizeUsername(value: String): String? =
    value.takeIf(usernamePattern::matches)?.lowercase(Locale.ROOT)

fun checkedCode(value: String): String =
    value.takeIf(codePattern::matches) ?: invalid("编码只能使用小写字母、数字、冒号、下划线和连字符，最长 120 字符")

fun checkedText(value: String, field: String, maxLength: Int = 120): String =
    value.trim().takeIf { it.isNotEmpty() && it.length <= maxLength }
        ?: invalid("$field 不能为空且最多为 $maxLength 字符")

fun checkedPassword(value: String): String =
    value.takeIf(::acceptableNewPassword)
        ?: invalid("密码至少为 12 个字符，最多为 72 个 UTF-8 字节")

fun acceptableNewPassword(value: String): Boolean =
    acceptableLoginPassword(value) && value.codePointCount(0, value.length) >= 12

fun acceptableLoginPassword(value: String): Boolean =
    value.length in 1..72 && value.toByteArray(Charsets.UTF_8).size <= 72

fun invalid(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)

fun missing(resource: String): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "未找到$resource")

fun conflict(message: String): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, message)

fun <T> query(): QueryWrapper<T> = QueryWrapper()

fun <T> validateParent(
    id: String,
    parentId: String?,
    rows: List<T>,
    idOf: (T) -> String,
    parentOf: (T) -> String?,
) {
    val byId = rows.associateBy(idOf)
    val visited = mutableSetOf(id)
    var ancestor = parentId
    while (ancestor != null) {
        if (!visited.add(ancestor)) invalid("父节点会形成循环")
        val row = byId[ancestor] ?: invalid("父节点不存在")
        ancestor = parentOf(row)
    }
}
