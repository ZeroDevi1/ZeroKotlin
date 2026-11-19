package dev.zerokotlin.core.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FieldMaskingTest {
    @Test
    fun `按 Unicode 码点保留首尾字符`() {
        assertEquals("🌱**树", FieldMasking.keepEdges("🌱成长树", prefix = 1, suffix = 1))
    }

    @Test
    fun `首尾长度覆盖整个值时仍遮盖所有字符`() {
        assertEquals("**", FieldMasking.keepEdges("甲乙", prefix = 1, suffix = 1))
    }
}
