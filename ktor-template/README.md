# ZeroKotlin

Ktor + Netty 后端和 ZeroKotlin Vue 3 管理工作台。Ktor 直接处理 HTTP、SSE 和 WebSocket，不启动 Spring Boot 后端。数据库复用 ZeroKotlin 的 Flyway 迁移。

## 启动

准备 JDK 25、Node.js 22.12 或更新版本和 pnpm。首次迁移空用户表时，`V9__initial_administrator.sql` 创建 `admin / admin`，只保存 BCrypt 哈希，不读取管理员密码环境变量。已有账号不会被覆盖。开发服务默认绑定回环地址，首次登录即修改密码，不要将默认凭据暴露到公网。

生成项目运行 `./gradlew run`；Windows 使用 `.\gradlew.bat run`。后端默认在 [本地 8080 端口](http://127.0.0.1:8080/health) 提供服务。另开终端进入 `web/`：

```sh
pnpm install --frozen-lockfile
pnpm dev
```

前端入口：[管理工作台](http://127.0.0.1:5173/)。

## 功能和兼容范围

- 管理台：账号和会话、用户、角色与数据权限、部门、岗位、权限点、菜单、字典、公告、个人收件箱、审计和运行监控。
- 后端：本地或对象存储、Excel 导入导出、SSE、STOMP 实时消息及 Quartz 调度规则；调度执行队列保存在数据库中。
- 数据库：默认 SQLite，也可配置 PostgreSQL 或 MySQL；安全状态可配置 Redis；Elasticsearch 与 Kafka 示例按需启用。接用旧数据时先用备份副本进行兼容核对。
- 开发：`/v3/api-docs`、`/v3/api-docs.yaml`、`/swagger-ui/` 和 `./gradlew codegen`。Ktor 生成的是 JDBC 路由，不使用 Spring MyBatis 模板。
- Spring 自动装配、Spring JPA 扩展示例及原 GraalVM Native Image 发布配置不适用于 Ktor。切换生产前需核对反代地址、TLS、验证码、审计与上传限制。

见[开发指南](docs/development.md)了解目录、配置、接口和 Spring Boot 迁移边界；已有项目合并新版模板见[升级指南](docs/template-upgrades.md)。
