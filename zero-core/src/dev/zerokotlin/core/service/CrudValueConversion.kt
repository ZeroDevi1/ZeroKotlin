package dev.zerokotlin.core.service

import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.UUID
import org.springframework.util.ClassUtils

/** 将筛选参数和 Excel 文本转为持久化或请求字段的类型。 */
internal object CrudValueConversion {
    private val parsers: Map<Class<*>, (String) -> Any> = mapOf(
        String::class.java to { it },
        Boolean::class.javaObjectType to {
            when (it.lowercase()) {
                "true", "1" -> true
                "false", "0" -> false
                else -> throw IllegalArgumentException("布尔值须为 true、false、1 或 0")
            }
        },
        Int::class.javaObjectType to String::toInt,
        Long::class.javaObjectType to String::toLong,
        Float::class.javaObjectType to { it.toFloat().also { value -> require(value.isFinite()) } },
        Double::class.javaObjectType to { it.toDouble().also { value -> require(value.isFinite()) } },
        BigInteger::class.java to { it.toBigInteger() },
        BigDecimal::class.java to { it.toBigDecimal() },
        UUID::class.java to UUID::fromString,
        Instant::class.java to Instant::parse,
        LocalDate::class.java to LocalDate::parse,
        LocalTime::class.java to LocalTime::parse,
        LocalDateTime::class.java to LocalDateTime::parse,
        OffsetDateTime::class.java to OffsetDateTime::parse,
    )

    fun supports(type: Class<*>): Boolean = ClassUtils.resolvePrimitiveIfNecessary(type) in parsers || type.isEnum

    fun parse(text: String, type: Class<*>): Any {
        val parser = parsers[ClassUtils.resolvePrimitiveIfNecessary(type)]
        if (parser != null) {
            return try {
                parser(if (type == String::class.java) text else text.trim())
            } catch (error: DateTimeParseException) {
                throw IllegalArgumentException(error)
            }
        }
        return type.enumConstants?.firstOrNull { (it as Enum<*>).name == text.trim() }
            ?: throw IllegalArgumentException("字段类型或枚举值无效")
    }
}
