# web · Next.js 前端

Next.js 15（App Router）+ TypeScript + Tailwind 4 + shadcn/ui。

## 三分钟跑起来

前置：Node 22+、pnpm 10+，且**后端已经在 8080 跑着**。

```bash
pnpm install
pnpm dev          # http://localhost:3000
```

后端没起来也能打开页面，只是所有请求会失败——`/api` 被代理到 `127.0.0.1:8080`，
连不上时前端会提示"网络异常，请确认后端已启动"。

```bash
pnpm lint         # ESLint
pnpm build        # 类型检查 + 生产构建（CI 跑这个）
```

## 为什么前端只写 `/api/...`

`next.config.ts` 里把 `/api/*` 重写到了后端：

```
/api/:path*  →  ${API_ORIGIN}/api/:path*
```

好处：同源之后没有跨域、没有预检，httpOnly 的 refresh Cookie 也能自动带上。
生产环境由 Nginx 做同样的反代（见 `实施方案/03-实施方案-v1.2.md`），前端代码不用改。

需要连别的后端时设环境变量：

```bash
API_ORIGIN=http://192.168.1.10:8080 pnpm dev
```

## 目录

```
src/
├─ app/
│   ├─ layout.tsx          全局壳：SessionProvider + Header + Toaster
│   ├─ page.tsx            首页（批次 2 接真实数据）
│   ├─ login/              登录页
│   └─ join/[token]/       邀请注册页（服务端 await params + 客户端表单）
├─ components/
│   ├─ site-header.tsx     导航（登录态 / 退出）
│   └─ ui/                 shadcn 组件（复制式安装，随意改）
└─ lib/
    ├─ api.ts              唯一的 HTTP 入口（解信封 / no-store / Bearer）
    ├─ api-schema.d.ts     ← 自动生成，不要手改
    ├─ types.ts            生成的类型别名
    ├─ session.tsx         会话（内存 token + refresh 恢复）
    └─ messages.ts         错误码 → 文案
```

## 类型从哪来

前端**不允许手写**接口类型（规范 T-06）。所有类型由 OpenAPI 生成：

```bash
pnpm exec openapi-typescript ../../docs/api/team-blog-api.openapi.yaml -o src/lib/api-schema.d.ts
```

改了后端契约就重新生成一次；CI 会检查"OpenAPI 改了但类型没更新"。

## 会话是怎么恢复的

access token 只放内存（放 localStorage 会被 XSS 偷，T-19）。刷新页面后：

```
SessionProvider 启动
  → POST /api/v1/auth/refresh（带 httpOnly 的 rt Cookie）
  → 拿到新 access token
  → GET /api/v1/me（确认用户还在）
  → 恢复登录态
```

## 常见错误

| 现象 | 原因 | 处理 |
|---|---|---|
| 页面能开，所有请求失败 | 后端没起，或端口不是 8080 | 起后端；或用 `API_ORIGIN` 指定 |
| 登录成功但刷新就掉线 | 浏览器拦了 Cookie，或 refresh 触发重放 | 确认走的是 3000 端口的代理，不是直连 8080 |
| `params` 是 Promise 的报错 | Next 15 里没 await | 见 `app/join/[token]/page.tsx` |
| 类型报"属性不存在" | 契约改了但没重新生成 | 跑一次上面的 `openapi-typescript` |

## 约定

编码规范见 `实施方案/07-开发规范.md`；模块讲解见 `docs/learning/`。
