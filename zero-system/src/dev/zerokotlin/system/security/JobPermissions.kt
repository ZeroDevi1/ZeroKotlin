package dev.zerokotlin.system.security

/** 定时任务目录、定义、调度状态、手动派发和日志各自使用的权限点。 */
const val JOB_READ = "system:job:read"
const val JOB_CREATE = "system:job:create"
const val JOB_UPDATE = "system:job:update"
const val JOB_DELETE = "system:job:delete"
const val JOB_PAUSE = "system:job:pause"
const val JOB_RESUME = "system:job:resume"
const val JOB_RUN = "system:job:run"
const val JOB_LOG_READ = "system:job:log:read"
