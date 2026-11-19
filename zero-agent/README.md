# zero-agent

`zero-agent` 是 ZeroKotlin 的可选 Kotlin 库，目前只包含构建配置和源码包占位。仓内依赖为 `zero-core`，通过版本目录引入 `io.agentscope:agentscope-core:2.0.4`。AgentScope Java 的 `agentscope-core` 提供 `ReActAgent` 等框架 API。[模块构建配置](build.gradle.kts) · [Gradle 版本目录](../gradle/libs.versions.toml) · [AgentScope Java 2.0 介绍](https://java.agentscope.io/v2/zh/intro) · [官方快速开始](https://java.agentscope.io/v2/zh/docs/quickstart)

## 按需装配

在仓库根目录执行。`withAgent` 默认关闭，显式传 `-PwithAgent=false` 效果相同。[首次启动](../README.md#首次启动)

```sh
./gradlew :zero-system:bootRun
```

启用时，`zero-system` 通过 `runtimeOnly` 装入库及其运行时依赖。当前模块只有占位源码，启用后无需 AgentScope 凭证。[启动项目的依赖声明](../zero-system/build.gradle.kts)

```sh
./gradlew :zero-system:bootRun -PwithAgent=true
```

打包 JVM 应用时使用同一个属性。

```sh
./gradlew :zero-system:bootJar -PwithAgent=true
```

用下列两条命令可比较 `runtimeClasspath`。预期第一份报告显示默认运行时依赖，第二份还会在 `project :zero-agent` 下列出 `io.agentscope:agentscope-core:2.0.4`。

```sh
./gradlew :zero-system:dependencies --configuration runtimeClasspath
./gradlew :zero-system:dependencies --configuration runtimeClasspath -PwithAgent=true
```

## 添加功能

1. 在 `src/dev/zerokotlin/agent/service/` 按实际用例编写业务服务，将 AgentScope 调用放在服务层。模块沿用根构建的 JDK 25、`src/` 源码根和 `resources/` 资源根。[源码占位](src/dev/zerokotlin/agent/service/AgentService.kt) · [根构建配置](../build.gradle.kts)
2. 用例需要 Spring 装配时，在 `src/dev/zerokotlin/agent/config/` 添加配置。需要 HTTP 接口时，在 `src/dev/zerokotlin/agent/` 下创建 `controller/` 和 `dto/`。装入运行时类路径后，`zero-system` 从 `dev.zerokotlin` 扫描组件。[应用入口](../zero-system/src/dev/zerokotlin/ZeroKotlinApplication.kt)

## 复用为新模块

1. 在仓库根目录创建 `zero-<名称>/`，参考本模块的 `build.gradle.kts` 保留 Kotlin 插件和 `implementation(project(":zero-core"))`。外部库按业务需求在版本目录声明，由新模块按需引用。
2. 在 `settings.gradle.kts` 的 `include(...)` 注册新模块，例如 `"zero-orders"`。[项目清单](../settings.gradle.kts)
3. 在 `zero-system/build.gradle.kts` 的 `dependencies` 块中装配新模块，例如 `runtimeOnly(project(":zero-orders"))`。需要默认关闭时，用 `if (enabled("withOrders"))` 包住该依赖。[启动项目的依赖声明](../zero-system/build.gradle.kts)
4. 在 `zero-<名称>/src/dev/zerokotlin/<包名>/` 下按职责创建源码包。[新建业务模块](../docs/new-module.md)
