# 运行监控与请求审计

| 入口 | 所需权限 | 内容 |
| --- | --- | --- |
| `GET /api/system/monitor` | `system:monitor:read` | CPU、内存、JVM 堆、磁盘与 Redis 即时快照。 |
| `GET /api/system/audits/requests` | `system:audit:read` | 请求审计分页。可按 `successful` 筛选，状态码低于 400 归入成功。 |
| `GET /api/system/audits/logins` | `system:audit:read` | 登录审计分页，含请求对端地址。 |

`V8__observability.sql` 初始化监控权限、菜单及审计表。Ktor 记录运行监控路由的操作者、HTTP 状态、耗时、异常类型和对端地址。审计写入失败时记录告警。沿用已有数据库时，历史离线归属地仍可读，新登录审计只记录对端地址。

CPU 比例为 0 到 1，内存和磁盘以字节计。磁盘指标覆盖应用工作目录所在的文件系统。平台无法提供的读数返回 `null`。使用进程内安全状态时，Redis 指标为 `DISABLED`。实际使用 Redis 时，连接异常或无权读取 `INFO` 会报告 `UNAVAILABLE`。
