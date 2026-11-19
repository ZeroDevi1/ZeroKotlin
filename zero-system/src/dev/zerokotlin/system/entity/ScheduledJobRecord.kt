package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.FieldStrategy
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

/** 定时任务定义及其持久化的调度游标。无自动计划时，计划配置、基准时刻和游标均为空。 */
@TableName("sys_scheduled_job")
class ScheduledJobRecord(
    @field:TableId(type = IdType.INPUT)
    var id: String = "",
    var name: String = "",
    var handler: String = "",
    @field:TableField(updateStrategy = FieldStrategy.ALWAYS)
    var scheduleJson: String? = null,
    @field:TableField(updateStrategy = FieldStrategy.ALWAYS)
    var anchorAt: Long? = null,
    @field:TableField(updateStrategy = FieldStrategy.ALWAYS)
    var nextFireAt: Long? = null,
    var paused: Int = 0,
)
