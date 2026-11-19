package dev.zerokotlin.core

/** 业务模块从此接口获取当前用户，具体认证方式由系统模块提供。 */
fun interface CurrentActor {
    fun requireUserId(): String
}
