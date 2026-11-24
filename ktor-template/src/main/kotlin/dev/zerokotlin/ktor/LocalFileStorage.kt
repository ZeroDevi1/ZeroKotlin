package dev.zerokotlin.ktor

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.UUID

/** 将文件保存在应用管理的私有目录中，以同目录原子移动发布完整文件。 */
internal class LocalFileStorage(private val directory: Path) : FileStorage {
    override val backend = FileStorageBackend.LOCAL
    override val locationId = storageLocationId(directory.toString())

    override fun put(key: UUID, input: InputStream, size: Long) {
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, ".$key-", ".part")
        try {
            val written = Files.newOutputStream(temporary).use { input.copyTo(it) }
            check(written == size) { "File size changed during upload" }
            Files.move(temporary, directory.resolve(key.toString()), ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    override fun open(key: UUID): InputStream? = try {
        Files.newInputStream(directory.resolve(key.toString()), NOFOLLOW_LINKS)
    } catch (_: NoSuchFileException) {
        null
    }

    override fun delete(key: UUID) {
        Files.deleteIfExists(directory.resolve(key.toString()))
    }
}
