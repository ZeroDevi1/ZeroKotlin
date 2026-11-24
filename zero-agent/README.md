# zero-agent

`zero-agent` 是可选的 AgentScope Java 扩展模块。它依赖 `zero-core`，使用 AgentScope Harness 和独立的 OpenAI 模型扩展。业务可在本模块的 `src/` 中创建服务，通过构造参数显式传递模型与运行配置。

## 按需装配

在项目根目录启动 Ktor，或生成运行分发包。

```sh
./gradlew :zero-system:run -PwithAgent=true
./gradlew :zero-system:installDist -PwithAgent=true
```

`withAgent` 默认关闭。启用时，`zero-agent` 及其依赖进入 `zero-system` 的运行时类路径。当前模块只有服务占位，接入实际业务时在 Ktor 应用显式装配所需服务。
