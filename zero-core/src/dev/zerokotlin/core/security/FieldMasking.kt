package dev.zerokotlin.core.security

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.BeanProperty
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.annotation.JsonSerialize

/**
 * 在 Jackson 输出字符串字段时遮盖中间部分。数据库中的值及反序列化输入保持原样。
 * @property prefix 保留的前缀 Unicode 码点数。
 * @property suffix 保留的后缀 Unicode 码点数。
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = MaskedStringSerializer::class)
annotation class Masked(val prefix: Int = 0, val suffix: Int = 0)

/** Jackson 按属性解析 [Masked] 的参数，缺省时遮盖整个字符串。 */
class MaskedStringSerializer private constructor(private val prefix: Int, private val suffix: Int) : ValueSerializer<String>() {
    constructor() : this(0, 0)

    override fun createContextual(context: SerializationContext, property: BeanProperty?): ValueSerializer<*> {
        val rule = property?.getAnnotation(Masked::class.java) ?: return this
        require(rule.prefix >= 0 && rule.suffix >= 0) { "脱敏保留长度必须为非负数" }
        return MaskedStringSerializer(rule.prefix, rule.suffix)
    }

    override fun serialize(value: String, generator: JsonGenerator, context: SerializationContext) {
        generator.writeString(FieldMasking.keepEdges(value, prefix, suffix))
    }
}

/** 在构建对外结果时遮盖字符串中的敏感部分。 */
object FieldMasking {
    /**
     * 保留指定数量的首尾 Unicode 码点，中间每个码点用一个星号遮盖。
     * 当字符串太短、无法按指定规则遮盖至少一个码点时，遮盖整个字符串。
     *
     * @param value 原始字符串。
     * @param prefix 首部可见码点数。
     * @param suffix 尾部可见码点数，默认为 0。
     * @return 可用于展示的脱敏字符串。
     */
    fun keepEdges(value: String, prefix: Int, suffix: Int = 0): String {
        require(prefix >= 0 && suffix >= 0)
        val length = value.codePointCount(0, value.length)
        if (prefix >= length || suffix >= length - prefix) return "*".repeat(length)
        val start = value.offsetByCodePoints(0, prefix)
        val end = value.offsetByCodePoints(0, length - suffix)
        return value.substring(0, start) + "*".repeat(length - prefix - suffix) + value.substring(end)
    }
}
