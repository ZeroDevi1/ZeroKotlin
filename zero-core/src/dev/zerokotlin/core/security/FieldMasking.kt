package dev.zerokotlin.core.security

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
