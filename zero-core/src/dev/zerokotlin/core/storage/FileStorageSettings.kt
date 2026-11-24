package dev.zerokotlin.core.storage

import com.aliyun.oss.ClientBuilderConfiguration
import com.aliyun.oss.OSSClientBuilder
import com.aliyun.oss.common.auth.DefaultCredentialProvider
import com.aliyun.oss.common.comm.SignVersion
import io.ktor.server.config.ApplicationConfig
import io.minio.MinioClient
import java.net.URI
import java.nio.file.Path
import java.util.Locale

fun configuredFileStorages(config: ApplicationConfig): FileStorages {
    fun option(name: String) = config.propertyOrNull("zero.$name")?.getString()?.takeIf(String::isNotBlank)
    fun required(name: String) = requireNotNull(option(name)) { "缺少文件存储配置 zero.$name" }
    fun endpoint(name: String): String {
        val url = required(name).trimEnd('/')
        val uri = runCatching { URI(url) }.getOrNull()
        require(uri != null && uri.scheme in setOf("http", "https") && uri.host != null &&
            (uri.port == -1 || uri.port in 1..65535) && uri.rawPath.isNullOrEmpty() &&
            uri.rawQuery == null && uri.rawFragment == null && uri.rawUserInfo == null
        ) { "文件存储端点须为 http(s)://host[:port]" }
        return url
    }

    val mode = FileStorageBackend.valueOf(required("filesMode").uppercase(Locale.ROOT))
    val stores = mutableListOf<FileStorage>(LocalFileStorage(Path.of(required("filesPath")).toAbsolutePath().normalize()))
    try {
        if (option("minioEndpoint") != null || mode == FileStorageBackend.MINIO) {
            val host = endpoint("minioEndpoint")
            val bucket = required("minioBucket")
            val client = MinioClient.builder().endpoint(host).credentials(required("minioAccessKey"), required("minioSecretKey"))
            option("minioRegion")?.let { client.region(it) }
            stores += MinioFileStorage(client.build(), bucket, storageLocationId(host, bucket))
        }
        if (option("aliyunEndpoint") != null || mode == FileStorageBackend.ALIYUN) {
            val host = endpoint("aliyunEndpoint")
            val bucket = required("aliyunBucket")
            val region = required("aliyunRegion")
            val clientConfig = ClientBuilderConfiguration().apply { setSignatureVersion(SignVersion.V4) }
            val credentials = DefaultCredentialProvider(required("aliyunAccessKey"), required("aliyunSecretKey"))
            val client = OSSClientBuilder.create().endpoint(host).region(region).credentialsProvider(credentials)
                .clientConfiguration(clientConfig).build()
            stores += AliyunFileStorage(client, bucket, storageLocationId(host, bucket, region))
        }
        return FileStorages(mode, stores)
    } catch (failure: Exception) {
        for (storage in stores) runCatching { storage.close() }.onFailure(failure::addSuppressed)
        throw failure
    }
}
