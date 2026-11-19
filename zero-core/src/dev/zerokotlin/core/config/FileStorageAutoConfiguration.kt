package dev.zerokotlin.core.config

import com.aliyun.oss.ClientBuilderConfiguration
import com.aliyun.oss.OSSClientBuilder
import com.aliyun.oss.common.auth.DefaultCredentialProvider
import com.aliyun.oss.common.comm.SignVersion
import dev.zerokotlin.core.service.AliyunFileStorage
import dev.zerokotlin.core.FileStorageBackend
import dev.zerokotlin.core.FileStorages
import dev.zerokotlin.core.service.LocalFileStorage
import dev.zerokotlin.core.service.MinioFileStorage
import dev.zerokotlin.core.storageLocationId
import io.minio.MinioClient
import java.net.URI
import java.nio.file.Path
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

/** 配置默认上传后端，并保留已配置的其他后端供历史文件读写。 */
@AutoConfiguration
@EnableConfigurationProperties(FileStorageProperties::class)
internal class FileStorageAutoConfiguration {
    /** 根据部署配置创建私有存储客户端。远端桶由部署方预先配置。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(FileStorages::class)
    fun fileStorages(properties: FileStorageProperties): FileStorages {
        require(properties.local.path.isNotBlank()) { "zero.files.local.path is required" }
        require(properties.mode != FileStorageBackend.MINIO || properties.minio != null) {
            "zero.files.minio is required in MINIO mode"
        }
        require(properties.mode != FileStorageBackend.ALIYUN || properties.aliyun != null) {
            "zero.files.aliyun is required in ALIYUN mode"
        }

        val local = LocalFileStorage(Path.of(properties.local.path).toAbsolutePath().normalize())
        val minio = properties.minio?.let(::minioStorage)
        val aliyun = try {
            properties.aliyun?.let(::aliyunStorage)
        } catch (failure: Exception) {
            runCatching { minio?.close() }.onFailure(failure::addSuppressed)
            throw failure
        }
        return FileStorages(properties.mode, listOfNotNull(local, minio, aliyun))
    }

    private fun minioStorage(properties: BucketProperties): MinioFileStorage {
        val prefix = "zero.files.minio"
        val endpoint = storageEndpoint(properties.endpoint, prefix)
        val bucket = required(properties.bucket, "$prefix.bucket")
        val builder = MinioClient.builder().endpoint(endpoint).credentials(
            required(properties.accessKey, "$prefix.access-key"),
            required(properties.secretKey, "$prefix.secret-key"),
        )
        properties.region?.let { builder.region(required(it, "$prefix.region")) }
        return MinioFileStorage(builder.build(), bucket, storageLocationId(endpoint, bucket))
    }

    private fun aliyunStorage(properties: BucketProperties): AliyunFileStorage {
        val prefix = "zero.files.aliyun"
        val endpoint = storageEndpoint(properties.endpoint, prefix)
        val bucket = required(properties.bucket, "$prefix.bucket")
        val region = required(properties.region, "$prefix.region")
        val config = ClientBuilderConfiguration().apply { setSignatureVersion(SignVersion.V4) }
        val credentials = DefaultCredentialProvider(
            required(properties.accessKey, "$prefix.access-key"),
            required(properties.secretKey, "$prefix.secret-key"),
        )
        val client = OSSClientBuilder.create().endpoint(endpoint).region(region)
            .credentialsProvider(credentials).clientConfiguration(config).build()
        return AliyunFileStorage(client, bucket, storageLocationId(endpoint, bucket, region))
    }

    private fun required(value: String?, property: String): String =
        requireNotNull(value?.takeIf(String::isNotBlank)) { "$property is required" }

    private fun storageEndpoint(value: String?, prefix: String): String {
        val endpoint = required(value, "$prefix.endpoint").trimEnd('/')
        val uri = runCatching { URI(endpoint) }.getOrNull()
        require(uri != null && uri.scheme in setOf("http", "https") && uri.host != null &&
            (uri.port == -1 || uri.port in 1..65535) && uri.rawPath.isNullOrEmpty() &&
            uri.rawQuery == null && uri.rawFragment == null && uri.rawUserInfo == null
        ) { "$prefix.endpoint must use http(s)://host[:port]" }
        return endpoint
    }
}

/** `zero.files` 配置。远端配置组留空时不创建对应客户端。 */
@ConfigurationProperties("zero.files")
internal class FileStorageProperties(
    val mode: FileStorageBackend = FileStorageBackend.LOCAL,
    val local: LocalFileProperties = LocalFileProperties(),
    val minio: BucketProperties? = null,
    val aliyun: BucketProperties? = null,
)

/** 本地文件目录。相对路径以应用的工作目录为基准。 */
internal class LocalFileProperties(val path: String = "data/files")

/** 私有对象存储桶配置。访问密钥由部署注入，阿里云 OSS 必须配置地域。 */
internal class BucketProperties(
    val endpoint: String? = null,
    val bucket: String? = null,
    val accessKey: String? = null,
    val secretKey: String? = null,
    val region: String? = null,
)
