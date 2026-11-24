# ZeroKotlin 管理工作台

Vue 3、TypeScript 与 Vue Router 实现的系统管理界面。前端使用后端菜单树和当前账号的权限控制可进入的页面，写入与行级范围由后端校验。

## 本地启动

1. 按[项目说明](../README.md#首次启动)启动 `zero-system` 并初始化内置管理员。
2. 在 `zero-web` 目录中使用 Node.js 22.12 及以上版本和 `package.json` 指定的 pnpm 版本安装依赖。
3. 启动 Vite。打开终端输出的 URL，用初始化的管理员账号登录。

```sh
pnpm install --frozen-lockfile
pnpm dev
```

Vite 将访问前缀下的同源 `/api` 请求转发到 `http://127.0.0.1:8080`。后端使用其他地址时，通过 Vite 进程的 `ZERO_WEB_API_TARGET` 指定后端的 HTTP(S) 源站地址。Bearer 凭据来自登录响应，按访问前缀存于当前标签页的 `sessionStorage`。

## 菜单与权限

`GET /api/auth/me` 提供 `administrator` 和权限映射，`GET /api/auth/me/menus` 提供该账号可见的菜单树。登录后优先打开可见的用户目录，其余账号进入收件箱。收件箱和个人中心对所有登录账号开放。

后端迁移初始化系统菜单。升级既有库时会保留已配置的同路径菜单。停用节点、停用祖先或缺少权限会同时影响侧边栏和直接访问。

| 页面 | 路径 | 页面读取权限 |
| --- | --- | --- |
| 用户 | `/users` | `system:user:read` |
| 角色 | `/roles` | `system:role:read` |
| 权限点 | `/permissions` | `system:permission:read` |
| 菜单 | `/menus` | `system:menu:read` |
| 部门 | `/departments` | `system:department:read` |
| 字典 | `/dictionaries` | `system:dictionary:read` |
| 审计 | `/audits` | `system:audit:read` |
| 岗位 | `/positions` | `system:position:read` |
| 公告管理 | `/announcements` | `system:announcement:read` |
| 文件管理 | `/files` | `system:file:read` |
| 实时消息 | `/realtime` | `system:message:read` |
| 定时任务 | `/jobs` | `system:job:read` |
| 运行监控 | `/operations` | `system:monitor:read` |
| 收件箱 | `/inbox` | 登录 |
| 个人中心 | `/profile` | 登录 |

扩展页面先在 [navigation.ts](src/router/navigation.ts) 的 `extensionPages` 注册固定路由、页面组件和页面权限，再使用[模块迁移或系统 API](../docs/system.md)发布含同一路径的菜单节点。已注册且有权访问的路径可成为导航链接。工作台支持任意层级的菜单分组。

## 部署

`pnpm build` 生成 `dist/`。在同一 HTTPS 站点上，将静态资源挂载到 Vite 的 `base`，把该前缀下的 `/api` 请求原样转发到后端。实时消息的 `/api/realtime/ws` 需要保留 WebSocket 升级。页面路径及刷新请求回退到该前缀下的 `index.html`。

Vite 的 `contextPath` 与后端 `application.conf` 的 `app.contextPath` 使用同一值。例如 `/apps/orders` 对应 `/apps/orders/` 的页面入口和 `/apps/orders/api/` 的接口前缀。
