# 定时任务

`/jobs` 的菜单权限为 `system:job:read`。升级时保留已配置的同路径菜单。任务和日志使用全局权限，所有接口均要求有效的 Bearer 会话。可以给业务角色分别授予下表各权限。内置管理员自动获得已启用的权限点。

## API

下表路径均相对于 `/api/system/jobs`。分页参数为 `page=1&size=20`，`size` 范围为 1–100，响应为 `{items,total,page,size}`。时间字段使用 Unix 毫秒。

| 方法和路径 | 权限 | 请求与响应 |
| --- | --- | --- |
| `GET /` | `system:job:read` | 分页返回 `{id,name,handler,schedule,paused,nextFireAt}`，按名称和 ID 升序。 |
| `GET /handlers` | `system:job:read` | 返回本地允许名单 `[{key,name}]`。 |
| `POST /` | `system:job:create` | 提交 `{name,handler,schedule}`，返回 201 和任务 `{id}`。 |
| `PUT /{id}` | `system:job:update` | 提交完整定义，返回 204。触发器变化时重算计划。 |
| `DELETE /{id}` | `system:job:delete` | 删除任务并取消尚未执行的手动请求，返回 204。执行历史仍可查询。 |
| `POST /{id}/pause` | `system:job:pause` | 暂停自动执行，返回 204。 |
| `POST /{id}/resume` | `system:job:resume` | 按原计划基准恢复，返回 204。 |
| `POST /{id}/run` | `system:job:run` | 提交 `{requestId}`，已入队时返回 202 和执行日志 `{id}`。暂停或仅手动的任务也可执行。 |
| `GET /logs` | `system:job:log:read` | 可传 `jobId` 精确筛选。按触发或提交时间及 ID 倒序分页，包含任务名称和实现键快照、来源、状态、开始和结束时间及结果说明。 |

现有实现键为 `heartbeat`（调度心跳）和 `purge-expired-sessions`（清除数据库中已经到期的会话）。任务名称去除首尾空白后最多 120 字符。手动请求标识为 32 位小写十六进制字符串。同一账号用原标识重试同一任务时，返回原执行 ID。复用到其他任务或跨账号复用返回 409。

## 触发器

`schedule` 省略或为 `null` 时，任务仅手动执行。自动计划须在下列配置中选择一项，`nextFireAt` 为空表示已暂停、无自动计划或已无未来触发点。

| 配置键 | 字段 | 触发方式 |
| --- | --- | --- |
| `cron` | `expression`、`timeZone` | Quartz 6 或 7 字段表达式。时区默认 `UTC`。 |
| `simple` | `interval`、`unit`、`repeatCount` | `unit` 为 `SECONDS`、`MINUTES` 或 `HOURS`。间隔为 1–86400，首次触发在一个间隔后。重复次数为首次之后的次数，范围 0–1000000，空值表示持续重复。 |
| `calendar` | `interval`、`unit`、`timeZone` | 按时区的日历间隔触发。单位 `DAYS`、`WEEKS`、`MONTHS`、`YEARS` 的间隔上限分别为 366、52、12、10。时区默认 `UTC`。 |
| `daily` | `daysOfWeek`、`startTime`、`endTime`、`interval`、`unit` | 星期使用 ISO 编号 1–7，周一为 1。时间格式 `HH:mm`，使用 JVM 默认时区。每天在指定窗口内按秒、分钟或小时触发，间隔最多 24 小时。 |

恢复暂停的任务时跳过暂停期间已过的触发点。修改名称或实现键保留原计划，修改触发器从保存时间起重算。有限固定间隔以原计划时间点计算次数，错过的时间点同样消耗对应次数。

## 持久化与执行

`V6__scheduled_jobs.sql` 使用 SQLite、PostgreSQL 和 MySQL 共用的 Flyway 目录。任务、下一次待派发时间和手动队列保存在项目数据库，Redis 可缺省。应用启动后，Ktor 后台协程每秒轮询待执行项，Quartz 用于计算计划时刻。重启补跑每个自动计划中已持久化的下一次到期项，然后跳到执行结束后的下一计划时刻。停机期间其余错过的时间点会跳过。未消费的手动请求会继续执行。

系统写入锁串行化任务变更和消费操作。管理审计的动作是 `job.create`、`job.update`、`job.delete`、`job.pause`、`job.resume` 和 `job.run`。执行日志状态为 `QUEUED`、`SUCCESS`、`FAILED` 或 `CANCELLED`。工作项在数据库事务中执行并写入结果。事务失败会回滚本轮变更，后续调度可重新消费原工作项。只有能随同一 JDBC 事务回滚的短时操作才适合加入本地允许名单。

数据库中的写入锁和游标防止重复消费。日内计划使用部署进程的 JVM 默认时区，因此使用同一数据库的进程须使用相同时区配置。当前调度采用每轮结束后等待一秒，每进程一个后台协程，手动触发与自动触发共享执行容量。

触发器类型参考 [上游调度类型](https://github.com/xia5800/springboot-kotlin-module/blob/e5e0cc7/zeta-model/zeta-model-quartz/src/main/kotlin/com/zeta/model/quartz/enums/ScheduleTypeEnum.kt)。日内触发行为参考 [Quartz 日内触发器源码](https://github.com/quartz-scheduler/quartz/blob/v2.5.2/quartz/src/main/java/org/quartz/impl/triggers/DailyTimeIntervalTriggerImpl.java)。
