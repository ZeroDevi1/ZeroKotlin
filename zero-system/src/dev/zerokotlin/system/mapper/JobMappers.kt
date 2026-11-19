package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.system.entity.JobRunRecord
import dev.zerokotlin.system.entity.ScheduledJobRecord
import org.apache.ibatis.annotations.Mapper

/** 读取任务定义和待触发时间，写入定义及调度游标。 */
@Mapper
interface ScheduledJobMapper : BaseMapper<ScheduledJobRecord>

/** 保存手动执行请求及运行结果，并按任务或时间查询日志。 */
@Mapper
interface JobRunMapper : BaseMapper<JobRunRecord>
