package dev.zerokotlin.core.security

import org.jsoup.Jsoup
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

/** 对显式接入的普通文本字段移除 HTML 标记和脚本内容。 */
class PlainTextInput internal constructor(private val enabled: Boolean) {
    private val cleaner = Cleaner(Safelist.none())

    /**
     * 清理已通过输入长度检查的文本。返回值应按普通文本渲染，由输出端负责上下文转义。
     *
     * @param value 允许清理的文本字段。
     * @return 清理后的文本，或关闭清理时的原始字符串。
     */
    fun clean(value: String): String =
        if (enabled && ('<' in value || '&' in value)) {
            cleaner.clean(Jsoup.parseBodyFragment(value)).body().wholeText()
        } else {
            value
        }
}
