# ZeroKotlin

小型全栈项目模板。后端使用 Ktor、Netty、kotlinx.serialization 和 Logback，前端使用 Vue 3、TypeScript 和 Vite。

## 启动服务

准备 JDK 25，在工程目录运行 Gradle Wrapper。首次运行会下载 Gradle 和依赖。

```powershell
.\gradlew.bat run
```

macOS 和 Linux 使用 `./gradlew run`。启动后访问 `http://127.0.0.1:8080/health`，应返回 HTTP 200，JSON 中的 `status` 为 `UP`，`name` 为初始化时指定的项目名。

另开终端，在项目根目录使用 Node.js 22.12 或更高版本和 `web/package.json` 指定的 pnpm 版本启动前端。

```sh
cd web
pnpm install --frozen-lockfile
pnpm dev
```

打开 `http://127.0.0.1:5173/`。页面会读取后端状态，输入姓名后可通过示例表单调用 JSON 接口。

## 示例接口

| 方法 | 地址 | 用途 |
| --- | --- | --- |
| `GET` | `http://127.0.0.1:8080/health` | 查看项目名和运行状态 |
| `POST` | `http://127.0.0.1:8080/api/hello` | 提交 `{"name":"Lin"}`，返回 `{"message":"你好，Lin"}` |

后端默认监听 `127.0.0.1:8080`，可用 `ZERO_HOST` 和 `ZERO_PORT` 覆盖。前后端访问前缀由初始化命令写入 [application.conf](src/main/resources/application.conf) 和 [vite.config.ts](web/vite.config.ts)。

- [中文开发指南](docs/development.md)包含前后端启动、请求示例、新增路由、错误处理和部署步骤。
- [模板升级指南](docs/template-upgrades.md)说明如何记录模板版本和把新版合入业务项目。
