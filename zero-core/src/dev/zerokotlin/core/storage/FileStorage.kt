package dev.zerokotlin.core.storage

import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

/** 部署配置可选的文件存储后端。 */
enum class FileStorageBackend { LOCAL, MINIO, ALIYUN }

/** 使用部署时配置的私有位置保存文件。对象键由调用方生成并保证唯一。 */
interface FileStorage : AutoCloseable {
    /** 持久化于文件元数据中的后端类型。 */
    val backend: FileStorageBackend

    /** 位置标识，用于阻止旧记录在变更目录或桶后访问错误的存储。 */
    val locationId: String

    /** 按已知字节数写入对象，调用方负责关闭输入流。 */
    fun put(key: UUID, input: InputStream, size: Long)

    /** 打开对象内容，不存在时返回 null。调用方必须关闭返回的流。 */
    fun open(key: UUID): InputStream?

    /** 删除指定对象。对象已不存在时也视为成功。 */
    fun delete(key: UUID)

    override fun close() = Unit
}

/** 选择新上传使用的后端，并按已保存的位置查找现有对象。 */
class FileStorages(defaultBackend: FileStorageBackend, backends: List<FileStorage>) : AutoCloseable {
    private val configured = backends.associateBy { it.backend }

    /** 新上传使用的文件存储。应用启动时必须已配置。 */
    val default: FileStorage = requireNotNull(configured[defaultBackend]) {
        "zero.files.mode=$defaultBackend requires its storage configuration"
    }

    /** 按后端及位置标识定位存储，部署缺少原位置时抛出 [IOException]。 */
    fun resolve(backend: FileStorageBackend, locationId: String): FileStorage =
        configured[backend]?.takeIf { it.locationId == locationId }
            ?: throw IOException("File storage location is not configured for $backend")

    /** 应用关闭时释放所有存储客户端。 */
    override fun close() {
        var firstFailure: Exception? = null
        for (storage in configured.values) {
            try {
                storage.close()
            } catch (failure: Exception) {
                if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
            }
        }
        firstFailure?.let { throw it }
    }
}

/** 根据目录或远端定位参数计算标识。凭据轮换时保持标识不变。 */
internal fun storageLocationId(vararg parts: String): String = HexFormat.of().formatHex(
    MessageDigest.getInstance("SHA-256").digest(parts.joinToString("\u0000").toByteArray(UTF_8)),
)
