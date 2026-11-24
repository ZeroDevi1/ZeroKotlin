package dev.zerokotlin.core.storage

import io.minio.GetObjectArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.errors.ErrorResponseException
import java.io.InputStream
import java.util.UUID

/** 使用 MinIO Java SDK 读写部署指定的私有桶。 */
internal class MinioFileStorage(
    private val client: MinioClient,
    private val bucket: String,
    override val locationId: String,
) : FileStorage {
    override val backend = FileStorageBackend.MINIO

    override fun put(key: UUID, input: InputStream, size: Long) {
        val request = PutObjectArgs.builder().bucket(bucket).`object`(key.toString())
        if (size == 0L) request.data(ByteArray(0), 0) else request.stream(input, size, -1L)
        client.putObject(request.build())
    }

    override fun open(key: UUID): InputStream? = try {
        client.getObject(GetObjectArgs.builder().bucket(bucket).`object`(key.toString()).build())
    } catch (failure: ErrorResponseException) {
        if (failure.errorResponse().code() != "NoSuchKey") throw failure
        null
    }

    override fun delete(key: UUID) {
        client.removeObject(RemoveObjectArgs.builder().bucket(bucket).`object`(key.toString()).build())
    }

    override fun close() = client.close()
}
