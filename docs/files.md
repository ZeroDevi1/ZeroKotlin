# 文件管理

在「系统管理 → 文件管理」查看文件。可以按文件名片段和业务分类筛选，列表按上传登记时间和 ID 倒序排列。`V4__files.sql` 在 SQLite、PostgreSQL 和 MySQL 的共用 Flyway 迁移中创建 `sys_file`、权限点和 `/files` 菜单。

## 接口与权限

所有接口使用 Bearer 会话。文件库按操作授权，授予某项权限即允许在整个文件库执行该操作。内置管理员自动获得已启用权限。进入 `/files` 页面需要 `system:file:read`，页面按各项权限显示操作按钮。

| 方法与路径 | 权限 | 成功响应 |
| --- | --- | --- |
| `GET /api/system/files` | `system:file:read` | `200`，分页元数据。`page` 从 1 开始，`size` 默认 20，范围 1–100。可选 `name` 为文件名片段，`bizType` 为精确分类编码。 |
| `POST /api/system/files` | `system:file:upload` | `201`，返回新文件元数据。请求为 `multipart/form-data`，包含一个 `file` 和可选的 `bizType`。 |
| `GET /api/system/files/{id}/download` | `system:file:download` | `200`，以 UTF-8 文件名返回 `application/octet-stream` 附件。 |
| `DELETE /api/system/files/{id}` | `system:file:delete` | `204`，文件已删除，包括重复删除。 |

元数据包括文件 ID、名称、业务分类、上传者声明的媒体类型、字节数、后端、状态、上传者 ID 和上传登记时间。文件名用于展示，存储对象键始终为 UUID。分类编码最多 120 字符，以小写字母或数字开头，可包含小写字母、数字、冒号、下划线及连字符。默认单文件上限为 50 MiB，整个请求上限为 51 MiB，可用 `spring.servlet.multipart.max-file-size` 和 `max-request-size` 覆盖。Vue 页面下载时会将整个响应读入 Blob 后交给浏览器保存。

## 部署配置

默认选择 `local`，文件保存在应用工作目录下的 `data/files`。使用 `zero.files.local.path` 指定私有的可写持久化目录，所在文件系统需支持同目录原子移动。多实例使用本地存储时，各实例须在同一绝对路径挂载同一存储卷。备份时需要同时涵盖数据库和对象内容。

使用远端后端时先创建私有桶，并授予部署身份对该桶对象的上传、读取和删除权限。MinIO 根据服务配置可需要查询桶地域，或在 `zero.files.minio.region` 中明确指定地域。阿里云 OSS 使用 V4 签名，要求 `region`。密钥由部署环境或密钥配置注入。下例配置两个远端位置并把 MinIO 设为新上传的默认后端。

```yaml
zero:
  files:
    mode: minio
    local:
      path: /srv/zerokotlin/files
    minio:
      endpoint: https://minio.example.com
      bucket: zero-files
      access-key: ${ZERO_MINIO_ACCESS_KEY}
      secret-key: ${ZERO_MINIO_SECRET_KEY}
    aliyun:
      endpoint: https://oss-cn-hangzhou.aliyuncs.com
      bucket: zero-files-hangzhou
      region: cn-hangzhou
      access-key: ${ZERO_OSS_ACCESS_KEY}
      secret-key: ${ZERO_OSS_SECRET_KEY}
```

`mode` 可设为 `local`、`minio` 或 `aliyun`。未使用的远端配置组可省略。切换默认后端只影响新上传，已有文件按记录中的后端和位置读取、删除。保留原目录、端点、桶配置可继续访问历史文件。位置不匹配时接口返回 `503`，密钥轮换无需调整元数据。远端 SDK 的 Native Image 支持尚未验证，远端部署使用 JVM 包。

[MinIO Java SDK](https://github.com/minio/minio-java/tree/9.0.3) · [阿里云 OSS Java SDK 和 V4 签名配置](https://www.alibabacloud.com/help/en/oss/developer-reference/oss-java-sdk/)

## 失败与清理

- **上传。** 先登记 `UPLOADING`，对象写入成功后在同一事务内改为 `AVAILABLE` 并写入 `file.upload` 审计。失败时回收尚未发布的对象。回收失败会保留记录，已经改为 `DELETING` 的文件可以在页面重试删除。
- **下载。** 仅允许下载 `AVAILABLE` 的文件。对象打开且再次授权后记录 `file.download`，随后开始传输。审计表示服务已准许开始下载。并发删除会阻止后续下载，已开始传输的请求可能继续完成。
- **删除。** 先提交 `DELETING` 状态，再删除对象。后端故障或元数据事务失败时可以用同一 ID 重试。对象已不存在也可完成删除。并发重试只会由一个请求删除元数据并同时提交 `file.delete` 审计。版本化桶的历史版本和未完成的分片由桶策略管理。
- **异常退出。** 进程中断或存储响应丢失时，可能留下长期 `UPLOADING` 记录、临时文件或无引用对象。排查时以文件 ID 对照 `sys_file` 和存储。对停留在 `UPLOADING` 的记录，停写并确认所有实例上的对应上传均已结束后，才可将确认放弃的记录改为 `DELETING` 并通过接口重试清理。删除本地 `.<文件 ID>-*.part` 前同样需要确认上传已结束。
