package dev.zerokotlin.ktor

import java.text.ParseException
import java.time.DateTimeException
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone
import org.quartz.CalendarIntervalScheduleBuilder
import org.quartz.CronExpression
import org.quartz.CronScheduleBuilder
import org.quartz.DailyTimeIntervalScheduleBuilder
import org.quartz.SimpleScheduleBuilder
import org.quartz.TimeOfDay
import org.quartz.Trigger
import org.quartz.TriggerBuilder

private val clockTime = Regex("[0-9]{2}:[0-9]{2}")

/** 按持久化基准重建 Quartz 触发器，计算严格晚于指定时刻的下一次计划。 */
internal fun JobSchedule.nextFire(anchor: Long, after: Long): Long? {
    if (listOfNotNull(cron, simple, calendar, daily).size != 1) {
        invalid("cron、simple、calendar、daily 中须恰好配置一种触发器")
    }
    val start = TriggerBuilder.newTrigger().startAt(Date(anchor))
    val trigger: Trigger = when {
        cron != null -> {
            val expression = checkedName(cron.expression)
            val parsed = try {
                CronExpression(expression)
            } catch (_: ParseException) {
                invalid("Quartz Cron 表达式无效")
            }
            start.withSchedule(CronScheduleBuilder.cronSchedule(parsed).inTimeZone(zone(cron.timeZone))).build()
        }
        simple != null -> {
            if (simple.interval !in 1..86_400) invalid("固定间隔须为 1–86400")
            val repeats = simple.repeatCount
            if (repeats != null && repeats !in 0..1_000_000) invalid("重复次数须为 0–1000000")
            val milliseconds = simple.interval * simple.unit.seconds * 1_000
            val builder = SimpleScheduleBuilder.simpleSchedule().withIntervalInMilliseconds(milliseconds)
            if (repeats == null) builder.repeatForever() else builder.withRepeatCount(repeats)
            start.startAt(Date(anchor + milliseconds)).withSchedule(builder).build()
        }
        calendar != null -> {
            if (calendar.interval !in 1..calendar.unit.maximum) invalid("日历间隔超出所选单位范围")
            val builder = CalendarIntervalScheduleBuilder.calendarIntervalSchedule()
                .withInterval(calendar.interval, calendar.unit.quartz)
                .inTimeZone(zone(calendar.timeZone))
                .preserveHourOfDayAcrossDaylightSavings(true)
            start.withSchedule(builder).build()
        }
        else -> start.withSchedule(checkNotNull(daily).builder()).build()
    }
    // Quartz 的日内触发器以秒为精度。去掉毫秒，避免跳过紧邻的下一个整秒。
    val cutoff = if (daily != null) after / 1_000 * 1_000 else after
    return trigger.getFireTimeAfter(Date(cutoff))?.time?.takeIf { it > after }
}

/** 约束日内间隔和星期编号，再转换为 Quartz 日内触发器。 */
private fun JobDaily.builder(): DailyTimeIntervalScheduleBuilder {
    if (daysOfWeek.isEmpty() || daysOfWeek.any { it !in 1..7 }) invalid("星期须选择 1–7 中的至少一天")
    if (interval < 1 || interval * unit.seconds > 86_400) invalid("日内间隔须大于零且不超过 24 小时")
    val from = localTime(startTime)
    val until = localTime(endTime)
    if (!until.isAfter(from)) invalid("结束时间须晚于开始时间")
    return DailyTimeIntervalScheduleBuilder.dailyTimeIntervalSchedule()
        .onDaysOfTheWeek(daysOfWeek.map { it % 7 + 1 }.toSet())
        .startingDailyAt(TimeOfDay(from.hour, from.minute))
        .endingDailyAt(TimeOfDay(until.hour, until.minute))
        .withInterval(interval, unit.quartz)
}

/** 将合法时区 ID 转换为 Quartz 所用的时区，避免无效 ID 被静默视为 GMT。 */
private fun zone(value: String): TimeZone = try {
    TimeZone.getTimeZone(ZoneId.of(value.trim().takeIf { it.length in 1..64 } ?: invalid("时区 ID 无效")))
} catch (_: DateTimeException) {
    invalid("时区 ID 无效")
}

/** 读取精确到分钟的日内时间。 */
private fun localTime(value: String): LocalTime {
    if (!clockTime.matches(value)) invalid("日内时间须使用 HH:mm 格式")
    return try {
        LocalTime.parse(value)
    } catch (_: DateTimeException) {
        invalid("日内时间无效")
    }
}
