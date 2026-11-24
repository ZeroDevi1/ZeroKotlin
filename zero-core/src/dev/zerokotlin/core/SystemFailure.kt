package dev.zerokotlin.core

/** 携带 HTTP 状态和可向调用方返回的错误消息。 */
class SystemFailure(val status: Int, message: String) : RuntimeException(message)
