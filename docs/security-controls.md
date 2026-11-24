# 安全控制

| 环境变量 | 默认值 | 行为 |
| --- | --- | --- |
| `ZERO_SECURITY_XSS_ENABLED` | `false` | 启用个人或账号显示名、公告标题的普通文本清理。 |
| `ZERO_SYSTEM_CAPTCHA_ENABLED` | `false` | 登录时要求一次性图形验证码。 |
| `ZERO_SYSTEM_CAPTCHA_TTL_SECONDS` | `120` | 挑战有效期，允许 15–600 秒。 |

启用文本清理后，公告标题 `<b>维护通知</b>` 保存为 `维护通知`。新模块应明确哪些字段按普通文本处理，并按输出上下文转义。

## 接口限流

| 操作 | 每个对端 IP 每 60 秒 |
| --- | --- |
| `GET /api/auth/captcha` | 20 次 |
| `POST /api/auth/login` | 30 次 |
| `PUT /api/auth/me/password` | 10 次 |

首个请求开启固定窗口。超额返回 429，`Retry-After` 表示可重试前的秒数。限流与验证码使用同一个安全状态后端。多实例共享状态的配置见[数据源和安全状态](data.md#安全状态)。

## 一次性登录验证码

调用 `GET /api/auth/captcha`。关闭时返回 204，启用时返回 200，包含 `id`、PNG 的 `imageBase64` 和 Unix 毫秒时间戳 `expiresAt`。登录时在原请求中附加 `captchaId` 与图片中的六位 `captchaCode`。字母忽略大小写。

服务端在首次校验有效 ID 时核销挑战。验证码缺失、错误、过期或重放返回 400。通过验证码校验后，错误凭据或已停用账号返回 401。使用进程内后端时，签发和登录须命中同一实例。
