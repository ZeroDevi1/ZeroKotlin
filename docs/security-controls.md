# 安全控制

## 配置

| Spring 属性 | 默认值 | 行为 |
| --- | --- | --- |
| `zero.security.xss.enabled` | `false` | 为已接入的普通文本字段清理 HTML 标记和脚本内容。 |
| `zero.security.xss.parameter-names` | 空列表 | 启用清理后，Servlet 过滤器处理 `/api/*` 下指定名称的查询和表单参数，包括同名的多个值。 |
| `zero.security.xss.exclude-paths` | 空列表 | 豁免参数过滤的应用内路径，支持 `/api/example/**` 等模式。 |
| `zero.security.rate-limit.enabled` | `true` | 启用标注了 `@ApiRateLimit` 的接口配额。 |
| `zero.system.audit.mask-remote-address` | `false` | 对登录和请求审计响应中的对端地址脱敏。 |
| `zero.system.captcha.enabled` | `false` | 登录时要求图形验证码。 |
| `zero.system.captcha.ttl-seconds` | `120` | 挑战有效期，单位为秒，允许 15～600。 |

例如，设置环境变量 `ZERO_SYSTEM_CAPTCHA_ENABLED=true` 启用登录验证码。四项控制均通过应用配置生效，无数据库迁移。

## 脱敏与输入清理

- `GET /api/system/audits/logins` 要求 `system:audit:read` 权限。启用地址脱敏后，`remoteAddress` 只显示前三个字符，其余以星号覆盖，例如 `192.0.2.42` 显示为 `192*******`。实际审计仍保存请求对端地址。模块的响应 DTO 可使用 `@get:Masked(prefix = 3, suffix = 4) val phone: String`。Jackson 在写 JSON 时按字段留出指定的首尾码点；原值仍可用于业务查询。
- 新建或编辑公告时，启用清理后将标题 `<b>维护通知</b>` 保存为 `维护通知`。新建账号和修改资料时，用户显示名应用同一规则。清理后为空的字段返回 400。公告正文、字典值、路径、账号和密码沿用各自的校验，令牌及二进制数据不经过该清理器。
- 在新模块中注入 `PlainTextInput`，为已校验长度且明确按普通文本处理的字段调用 `clean(value)`。输出端按上下文转义，Vue 用插值显示这些字段。[jsoup Safelist](https://jsoup.org/apidocs/org/jsoup/safety/Safelist)
- 例如，配置 `zero.security.xss.parameter-names: [keyword]` 即可为新模块的搜索词启用相同规则。根据字段契约选择可清理的名称。过滤器跳过认证路径、WebSocket 握手和 multipart 上传。

## 接口限流

| 操作 | 计数维度 | 配额 |
| --- | --- | --- |
| `GET /api/auth/captcha` | 请求对端 IP | 20 次 / 60 秒 |
| `POST /api/auth/login` | 请求对端 IP | 30 次 / 60 秒 |
| `PUT /api/inbox/read-all` | 当前用户 ID | 20 次 / 60 秒 |

首个请求开启固定窗口。超额请求返回 429，`Retry-After` 给出可重试前的秒数。限流在 Bearer 过滤链之后、控制器参数绑定前执行。控制器新增配额时，可声明 `@ApiRateLimit(key = "orders.list", requests = 60, windowSeconds = 60, by = RateLimitBy.USER)`，并对接口应用原有操作权限控制。

- IP 取自 Servlet `remoteAddr`。反向代理部署中，应用应只通过可信代理和容器配置恢复客户端地址。共享出口地址的客户端共享 IP 配额。
- 限流和验证码复用内置缓存选型。默认进程内计数，本地窗口容量沿用 `zero.cache.caffeine.maximum-size`，容量满时新配额键返回 429，等待已有窗口过期。应用替换内置 `CacheManager` 时，安全状态默认保存在进程内。
- 多实例部署使用相同 Redis 主机、库及键前缀。设置 `zero.cache.mode=redis` 和 `zero.cache.redis.host` 可要求启动时必须连接 Redis。缓存后端使用 Redis 时，固定窗口计数和验证码核销通过 Redis Lua 脚本原子执行。Redis 运行故障返回 503。Redis ACL 须允许应用键空间的 `EVALSHA`、`EVAL`、`GET`、`SET`、`PTTL`、`INCR` 和 `DEL`。缓存的 `auto` 模式允许启动时回退至本地。[缓存后端配置](data.md#缓存)

## 一次性登录验证码

1. 匿名调用 `GET /api/auth/captcha`。关闭时返回 204。开启时返回 200，包含 `id`、`imageBase64` 和 Unix 毫秒时间戳 `expiresAt`。图片格式为 PNG，响应禁止缓存。
2. 读取图片中的六位字符，然后提交 `POST /api/auth/login`。字母忽略大小写。

   ```json
   {"username":"admin","password":"<密码>","captchaId":"<签发响应中的 id>","captchaCode":"<图中六位字符>"}
   ```

3. 启用时，每个有效 ID 首次参与校验即被核销，包括答案错误或密码错误。验证码缺失、错误、过期或重放返回 400，重新获取挑战后再登录。通过验证码校验后，错误凭据或已停用账号返回 401。关闭验证码时，旧客户端仍可仅提交 `username` 和 `password`。

浏览器登录页会查询验证码状态，并在需要时显示输入框。点击图片可获取新挑战。使用本地后端时，签发和登录须命中同一进程，进程重启会使旧挑战失效。使用 Redis 时，可以从一个实例签发并在另一个实例提交，同一 ID 的并发登录请求最多有一次通过验证码校验。

验证码校验失败会写入登录失败审计，包含格式有效的规范化登录名和对端地址。签发验证码和限流提前拒绝的请求未进入登录认证流程。配置离线 IP 库后，登录审计也会保存可查到的归属地。
