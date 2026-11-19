package dev.zerokotlin.core

/** 业务模块从此接口获取当前用户，具体认证方式由系统模块提供。 */
fun interface CurrentActor {
    /**
     * 从当前认证上下文取得用户 ID。缺少系统用户主体时，系统模块抛出 HTTP 401 异常。
     *
     * @return 已认证用户的业务 ID。
     */
    fun requireUserId(): String
}
