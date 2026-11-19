package dev.zerokotlin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** ZeroKotlin 的 Spring Boot 入口配置，使用应用根包作为默认组件扫描起点。 */
@SpringBootApplication
class ZeroKotlinApplication

/**
 * 启动 ZeroKotlin 的 Spring 应用上下文。
 *
 * @param args 原样传给 Spring Boot 的命令行参数。
 */
fun main(args: Array<String>) {
    runApplication<ZeroKotlinApplication>(*args)
}
