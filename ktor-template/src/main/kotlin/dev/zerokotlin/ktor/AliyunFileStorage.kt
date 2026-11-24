package dev.zerokotlin.ktor

import com.aliyun.oss.OSS
import com.aliyun.oss.OSSException
import com.aliyun.oss.model.ObjectMetadata
import java.io.InputStream
import java.util.UUID

/** 使用阿里云 OSS Java SDK 读写部署指定的私有桶。 */
internal class AliyunFileStorage(
    private val client: OSS,
    private val bucket: String,
    override val locationId: String,
) : FileStorage {
    override val backend = FileStorageBackend.ALIYUN

    override fun put(key: UUID, input: InputStream, size: Long) {
        val metadata = ObjectMetadata().apply { contentLength = size }
        client.putObject(bucket, key.toString(), input, metadata)
    }

    override fun open(key: UUID): InputStream? = try {
        client.getObject(bucket, key.toString()).objectContent
    } catch (failure: OSSException) {
        if (failure.errorCode != "NoSuchKey") throw failure
        null
    }

    override fun delete(key: UUID) {
        client.deleteObject(bucket, key.toString())
    }

    override fun close() = client.shutdown()
}
