package dev.zerokotlin.system.security

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/** 使用固定字形生成灰度 PNG，可在没有桌面图形环境的服务器中运行。 */
internal object LoginCaptchaImage {
    private const val WIDTH = 160
    private const val HEIGHT = 46
    private const val SCALE = 4

    /** 字形按从上到下的 7 行定义，每行用低 5 位表示像素。 */
    private val glyphs = mapOf(
        '2' to intArrayOf(14, 17, 1, 2, 4, 8, 31),
        '3' to intArrayOf(30, 1, 1, 14, 1, 1, 30),
        '4' to intArrayOf(2, 6, 10, 18, 31, 2, 2),
        '5' to intArrayOf(31, 16, 16, 30, 1, 1, 30),
        '6' to intArrayOf(14, 16, 16, 30, 17, 17, 14),
        '7' to intArrayOf(31, 1, 2, 4, 8, 8, 8),
        '8' to intArrayOf(14, 17, 17, 14, 17, 17, 14),
        '9' to intArrayOf(14, 17, 17, 15, 1, 1, 14),
        'A' to intArrayOf(14, 17, 17, 31, 17, 17, 17),
        'C' to intArrayOf(14, 17, 16, 16, 16, 17, 14),
        'D' to intArrayOf(30, 17, 17, 17, 17, 17, 30),
        'E' to intArrayOf(31, 16, 16, 30, 16, 16, 31),
        'F' to intArrayOf(31, 16, 16, 30, 16, 16, 16),
        'H' to intArrayOf(17, 17, 17, 31, 17, 17, 17),
        'J' to intArrayOf(7, 2, 2, 2, 2, 18, 12),
        'K' to intArrayOf(17, 18, 20, 24, 20, 18, 17),
        'M' to intArrayOf(17, 27, 21, 21, 17, 17, 17),
        'N' to intArrayOf(17, 25, 21, 19, 17, 17, 17),
        'P' to intArrayOf(30, 17, 17, 30, 16, 16, 16),
        'R' to intArrayOf(30, 17, 17, 30, 20, 18, 17),
        'T' to intArrayOf(31, 4, 4, 4, 4, 4, 4),
        'U' to intArrayOf(17, 17, 17, 17, 17, 17, 14),
        'V' to intArrayOf(17, 17, 17, 17, 17, 10, 4),
        'W' to intArrayOf(17, 17, 17, 21, 21, 27, 17),
        'X' to intArrayOf(17, 17, 10, 4, 10, 17, 17),
        'Y' to intArrayOf(17, 17, 10, 4, 4, 4, 4),
    )

    /** 可用于挑战的 ASCII 字符。 */
    val alphabet: String = glyphs.keys.joinToString("")

    /** 将验证码绘制为含随机位置、斜切和噪点的 Base64 PNG。 */
    fun render(code: String, random: SecureRandom): String {
        val stride = WIDTH + 1
        val pixels = ByteArray(stride * HEIGHT) { 248.toByte() }
        for (y in 0 until HEIGHT) pixels[y * stride] = 0 // 每行开头的 PNG 过滤类型为 0。
        repeat(360) {
            val position = random.nextInt(HEIGHT) * stride + 1 + random.nextInt(WIDTH)
            pixels[position] = random.nextInt(130, 228).toByte()
        }
        code.forEachIndexed { index, character ->
            val x = 8 + index * 24 + random.nextInt(5)
            val y = 6 + random.nextInt(7)
            val slant = random.nextInt(-2, 3)
            val shade = random.nextInt(28, 78).toByte()
            glyphs.getValue(character).forEachIndexed { row, mask ->
                for (column in 0 until 5) {
                    if ((mask and (1 shl (4 - column))) == 0) continue
                    val left = x + column * SCALE + (row - 3) * slant / 2
                    val top = y + row * SCALE
                    repeat(SCALE) { offset ->
                        val start = (top + offset) * stride + 1 + left
                        pixels.fill(shade, start, start + SCALE)
                    }
                }
            }
        }
        repeat(2) {
            val startY = random.nextInt(HEIGHT)
            val endY = random.nextInt(HEIGHT)
            val shade = random.nextInt(70, 155).toByte()
            for (x in 0 until WIDTH) {
                val y = startY + (endY - startY) * x / (WIDTH - 1)
                pixels[y * stride + 1 + x] = shade
            }
        }

        val header = ByteArrayOutputStream()
        DataOutputStream(header).use {
            it.writeInt(WIDTH)
            it.writeInt(HEIGHT)
            it.write(byteArrayOf(8, 0, 0, 0, 0))
        }
        val compressed = ByteArrayOutputStream()
        DeflaterOutputStream(compressed).use { it.write(pixels) }
        val png = ByteArrayOutputStream()
        DataOutputStream(png).use {
            it.writeLong(0x89504E470D0A1A0AuL.toLong())
            it.writeChunk("IHDR", header.toByteArray())
            it.writeChunk("IDAT", compressed.toByteArray())
            it.writeChunk("IEND", ByteArray(0))
        }
        return Base64.getEncoder().encodeToString(png.toByteArray())
    }

    /** 写入 PNG 区块，包含类型和内容的 CRC-32。 */
    private fun DataOutputStream.writeChunk(type: String, data: ByteArray) {
        val tag = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(tag)
            update(data)
        }
        writeInt(data.size)
        write(tag)
        write(data)
        writeInt(crc.value.toInt())
    }
}
