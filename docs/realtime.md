# 实时消息

管理工作台通过 `/realtime` 接收实时群发和个人消息。页面建立 STOMP 连接后查询在线人数，每 15 秒刷新一次。网络恢复后自动重连并重新订阅。离开页面或退出登录时关闭连接。

## 权限

`V5__realtime.sql` 为系统管理分组初始化菜单及三个权限点。内置管理员拥有全部启用的权限点，业务角色按需授权。

| 权限 | 用途 |
| --- | --- |
| `system:message:read` | 进入页面、订阅和接收实时消息、查询在线人数 |
| `system:message:broadcast` | 向当前实例中订阅群发主题的用户发送消息 |
| `system:message:private` | 按该权限的数据范围向指定启用用户私发，接收者填写用户 ID |

## HTTP 和 STOMP 接口

HTTP 请求使用 `Authorization: Bearer <token>`。发送正文修剪首尾空白后须有 1～2 000 个 UTF-16 代码单元。

| HTTP 接口 | 请求体 | 成功响应 |
| --- | --- | --- |
| `GET /api/realtime/online` | — | `200`，`{"count": 2}`。同一用户的多个已连接标签页只计一次。 |
| `POST /api/realtime/broadcast` | `{"body": "大家好"}` | `204`，已提交给消息代理。 |
| `POST /api/realtime/private` | `{"recipientId": "用户 ID", "body": "你好"}` | `204`，已提交给消息代理。 |

浏览器对同源 `/api/realtime/ws` 发起 WebSocket 升级，在 STOMP `CONNECT` 帧中以 `Authorization: Bearer <token>` 提交凭据。服务端根据会话确定用户 ID，连接凭据不会放入 URL。允许的订阅地址如下。

- `/topic/realtime/broadcast` 接收群发。
- `/user/queue/realtime/private` 接收发给当前用户的私发。

客户端 `SEND` 帧和其他订阅地址会被拒绝。订阅和消息投递时会复核读取权限及会话有效性，已到期或注销的会话无法继续收消息。在线人数反映本实例已建立且尚未断开的连接用户。

STOMP `MESSAGE` 帧正文是 JSON 对象，字段为 `id`、`senderId`、`senderName`、`body` 和 `sentAt`，时间使用 Unix 毫秒。发送者字段取自服务端会话。提交前写入操作审计，动作使用对应的发送权限编码，目标记为 `broadcast` 或接收用户 ID，审计只记录操作元数据。

## SSE 通信演示

在管理端「实时消息 → SSE 通信演示」点击「接收示例事件」，页面会逐帧显示服务端发送的三个 `tick` 和一个 `done`。流自动结束，也可以手动断开。`GET /api/realtime/sse/demo` 沿用登录会话和 `system:message:read` 权限。浏览器使用带 Bearer 请求头的 `fetch`，令牌只经请求头传递。

使用具备该权限的账号登录，把登录响应的令牌放入当前终端的 `ACCESS_TOKEN`，即可直接观察事件到达。

```sh
curl -N --fail-with-body -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Accept: text/event-stream" http://localhost:8080/api/realtime/sse/demo
```

请求带 `Last-Event-ID: 2` 时只发送第 3 个 tick 和结束事件。最后收到的 ID 已为 3 时返回 204，非法 ID 返回 400。代理需为该路径关闭响应缓冲，以便客户端即时显示事件。示例事件序列仅用于演示传输。[Spring MVC 流式响应](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html)

## 部署与范围

- Vite 开发和预览代理会转发该路径上的 WebSocket 升级。部署时同源反向代理也须转发升级，并让后端看到浏览器访问的主机、端口和协议，以通过同源检查。HTTPS 页面使用 `wss://`。
- 消息代理和在线统计均限于当前进程。`204` 表示提交成功，接收者须在线且已订阅才有机会接收。消息只在页面内显示最近 100 条，离线消息和断线期间的消息无补发。已发布公告及已读状态仍在 `/inbox` 查看。
- 集成时在 `zero-web/src/router/navigation.ts` 登记 `/realtime`、`RealtimeView.vue` 和 `system:message:read`，使迁移生成的菜单可进入页面。

[Spring Boot WebSocket](https://docs.spring.io/spring-boot/reference/messaging/websockets.html) · [STOMP.js 客户端](https://github.com/stomp-js/stompjs/tree/v7.5.0)
