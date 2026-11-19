package dev.zerokotlin.system.entity

import com.baomidou.mybatisplus.annotation.FieldStrategy
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

/**
 * 定时任务定义及其持久化的调度游标。
 *
 * @property id 任务定义的主键。
 * @property name 管理界面展示的任务名称。
 * @property handler 应用注册的任务处理器编码。
 * @property scheduleJson 自动计划的 JSON 配置，仅手动触发时为空。
 * @property anchorAt 自动计划的基准时刻，使用 Unix 毫秒时间戳，仅手动触发时为空。
 * @property nextFireAt 下一次待派发的 Unix 毫秒时间戳，无待派发计划时为空。
 * @property paused 自动计划的暂停标志，`1` 表示暂停，`0` 表示未暂停。
 */
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
