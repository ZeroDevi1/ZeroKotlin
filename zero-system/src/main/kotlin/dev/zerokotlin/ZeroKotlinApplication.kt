package dev.zerokotlin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ZeroKotlinApplication

fun main(args: Array<String>) {
    runApplication<ZeroKotlinApplication>(*args)
}
