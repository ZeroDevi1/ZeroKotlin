# 运行监控与请求审计

## 使用入口

| 入口 | 所需权限 | 内容 |
| --- | --- | --- |
| 管理端「系统管理 → 运行监控」`/operations` · `GET /api/system/monitor` | `system:monitor:read` | CPU、系统内存、JVM 堆、磁盘与 Redis 即时快照。页面打开时采样，可手动刷新。 |
| 管理端「系统管理 → 审计日志」· `GET /api/system/audits/requests` | `system:audit:read` | 按时间倒序显示请求审计。支持 `page`、`size` 和 `successful`，状态码低于 400 时归入成功。 |
| 管理端「审计日志 → 登录审计」· `GET /api/system/audits/logins` | `system:audit:read` | 显示对端 IP（按审计地址脱敏配置展示）与可选的离线归属地 `ipRegion`。 |

`V8__observability.sql` 为 SQLite、PostgreSQL 和 MySQL 提供共用迁移。新增监控权限和菜单，普通角色通过角色管理按需授权。旧登录记录的归属地为空，新登录记录在写入时查询。

## 离线 IP 与审计配置

```yaml
zero:
  observability:
    ip-region:
      ipv4-path: /path/to/ip2region_v4.xdb
      ipv6-path: /path/to/ip2region_v6.xdb
    request-audit:
      enabled: true
      include-user-agent: false
```

应用使用 ip2region Java 读取本地 xdb，支持 2.0 结构的 IPv4 库及 3.0 结构的 IPv4、IPv6 库。部署方可准备有权使用的数据文件，分别配置地址族，也可省略任一路径。启动时读取向量索引，替换数据文件后重启应用。缺库、文件无效或地址查无结果时，归属地为空，审计继续保存 Servlet 提供的对端 IP。数据获取与使用范围参见 [ip2region 官方说明](https://github.com/lionsoul2014/ip2region/blob/master/README_zh.md)。

在 Spring MVC 控制器方法上使用 `@RequestAudit("monitor.read")` 这样的稳定动作标识。默认标注了运行监控接口。记录包括操作者 ID、请求方法、路由模板、HTTP 状态、处理耗时、异常类型、对端 IP 和可用的归属地。采集范围限于这些字段。显式开启后额外保存最长 512 个字符的 `User-Agent`。

审计范围从认证过滤链放行且 MVC 匹配到标注方法开始。MVC 完成处理后独立写入，写入失败时服务端记告警，原请求仍按既定结果返回。已有登录及事务操作审计的端点沿用各自的写入路径。

## 监控指标范围

- CPU 和系统内存以当前 Java 进程看到的环境为准。CPU 比例为 0 到 1，内存和磁盘以字节计。磁盘统计覆盖应用工作目录所在的文件系统。平台无法提供的读数返回 `null`，页面用空状态或横线表示。
- Redis 使用内置缓存实际选中的连接。Caffeine 或自动降级时状态为 `DISABLED`，并正常返回其余指标。启用 Redis 后若连接不可达或账号缺少 `INFO` 权限，状态为 `UNAVAILABLE`。可读时状态为 `AVAILABLE`，指标白名单包含版本、客户端数、已用内存和运行时长。
- 接口通过固定响应字段返回上述指标。
