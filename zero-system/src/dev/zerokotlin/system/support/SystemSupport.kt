package dev.zerokotlin.system.support

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import java.util.Locale
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/** 用户名允许 3～64 个 ASCII 字符。首字符是字母或数字，其余还可使用点、下划线和连字符。 */
private val usernamePattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{2,63}")

/** 业务编码允许 1～120 个 ASCII 字符。首字符是小写字母或数字，其余还可使用冒号、下划线和连字符。 */
private val codePattern = Regex("[a-z0-9][a-z0-9:_-]{0,119}")

/**
 * 生成系统实体使用的随机标识。
 *
 * @return 带连字符的 UUID 字符串。
 */
fun newId(): String = UUID.randomUUID().toString()

/**
 * 将布尔状态转换为数据库约定的整数标志。
 *
 * @receiver 待持久化的布尔状态。
 * @return true 对应 1，false 对应 0。
 */
fun Boolean.flag(): Int = if (this) 1 else 0

/**
 * 校验更新请求提供了必填的启用状态，再转换为数据库标志。
 *
 * @receiver 请求中的可空启用状态，为 null 时抛出 HTTP 400 异常。
 * @return true 对应 1，false 对应 0。
 */
fun Boolean?.requiredFlag(): Int = this?.flag() ?: invalid("enabled 为必填项")

/**
 * 检查用户名格式并使用与区域设置无关的规则转换为小写。
 *
 * @param value 提交的用户名，首尾空白会导致校验失败。
 * @return 可用于查库或保存的用户名，格式无效时返回 null。
 */
fun normalizeUsername(value: String): String? =
    value.takeIf(usernamePattern::matches)?.lowercase(Locale.ROOT)

/**
 * 校验业务编码的长度、字符范围和首字符。
 *
 * @param value 待校验的原始编码。
 * @return 校验通过的原始编码。
 * @throws ResponseStatusException 编码格式无效时抛出 HTTP 400 异常。
 */
fun checkedCode(value: String): String =
    value.takeIf(codePattern::matches) ?: invalid("编码只能使用小写字母、数字、冒号、下划线和连字符，最长 120 字符")

/**
 * 去除首尾空白并校验文本长度。
 *
 * @param value 提交的原始文本。
 * @param field 错误消息中使用的字段名。
 * @param maxLength 修剪后的最大长度，以 UTF-16 代码单元计，默认 120。
 * @return 修剪后的非空文本。
 * @throws ResponseStatusException 修剪后为空或超过上限时抛出 HTTP 400 异常。
 */
fun checkedText(value: String, field: String, maxLength: Int = 120): String =
    value.trim().takeIf { it.isNotEmpty() && it.length <= maxLength }
        ?: invalid("$field 不能为空且最多为 $maxLength 字符")

/**
 * 校验新密码的长度，供后续保存密码哈希使用。
 *
 * @param value 提交的新密码。
 * @return 原始密码文本。
 * @throws ResponseStatusException 少于 12 个 Unicode 码点或超过 72 个 UTF-8 字节时抛出 HTTP 400 异常。
 */
fun checkedPassword(value: String): String =
    value.takeIf(::acceptableNewPassword)
        ?: invalid("密码至少为 12 个字符，最多为 72 个 UTF-8 字节")

/**
 * 判断密码是否满足新建账号或更换密码的长度要求。
 *
 * @param value 待校验的新密码。
 * @return 至少含 12 个 Unicode 码点且符合登录密码上限时为 true。
 */
fun acceptableNewPassword(value: String): Boolean =
    acceptableLoginPassword(value) && value.codePointCount(0, value.length) >= 12

/**
 * 在进行哈希校验前检查登录密码的长度。
 *
 * @param value 用户提交的密码。
 * @return UTF-16 长度为 1～72 且 UTF-8 编码后最多占 72 字节时为 true。
 */
fun acceptableLoginPassword(value: String): Boolean =
    value.length in 1..72 && value.toByteArray(Charsets.UTF_8).size <= 72

/**
 * 终止请求，报告参数或业务校验失败。
 *
 * @param message 写入异常的校验失败说明。
 * @throws ResponseStatusException 始终抛出 HTTP 400 异常。
 */
fun invalid(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)

/**
 * 终止请求，报告找不到目标资源。
 *
 * @param resource 用于生成缺失提示的资源名称。
 * @throws ResponseStatusException 始终抛出 HTTP 404 异常。
 */
fun missing(resource: String): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "未找到$resource")

/**
 * 终止请求，报告唯一性或状态冲突。
 *
 * @param message 写入异常的冲突原因。
 * @throws ResponseStatusException 始终抛出 HTTP 409 异常。
 */
fun conflict(message: String): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, message)

/**
 * 创建用于拼装查询条件的 MyBatis-Plus 包装器。
 *
 * @param T 查询对应的实体类型。
 * @return 空的查询条件包装器，调用方可继续追加条件。
 */
fun <T> query(): QueryWrapper<T> = QueryWrapper()

/**
 * 沿候选父节点的祖先链向上检查，确保引用的祖先存在且层级无环。
 *
 * @param T 部门、菜单等树节点的记录类型。
 * @param id 正在新增或调整的节点 ID。
 * @param parentId 候选父节点的 ID，为 null 时表示将节点置于根层级。
 * @param rows 当前树中的节点记录，须涵盖待检查的祖先链。
 * @param idOf 从记录读取节点 ID。
 * @param parentOf 从记录读取父节点 ID，根节点返回 null。
 * @throws ResponseStatusException 祖先缺失或链路成环时抛出 HTTP 400 异常。
 */
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
