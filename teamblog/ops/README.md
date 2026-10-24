# 部署（批次 5 · G5 门禁）

四个服务：`db`（PostgreSQL 16）→ `api`（Spring Boot）→ `web`（Next.js）→ `nginx`（对外唯一入口）。

```
浏览器 ──> nginx :8088 ──┬─ /            → web:3000
                         ├─ /api/        → api:8080
                         └─ /uploads/    → 共享卷（nginx 直接读）
```

## 一条命令起来

```bash
cd teamblog/ops/compose
cp .env.example .env            # 改掉 POSTGRES_PASSWORD 和 JWT_SECRET
docker compose up -d --build
docker compose ps
```

> `.env` 必须和 `compose.yaml` **同目录**：Compose 只读取 compose 文件所在目录下的 `.env`，
> 不会去上一层找。放错位置的表现是一执行就报
> `required variable POSTGRES_PASSWORD is missing a value`——看起来像变量没配，
> 其实是文件没被找到。

打开 <http://localhost:8088>。

首次启动时 **Flyway 自动跑迁移**（应用启动的一部分），不需要手工执行任何 SQL。
这就是"新机器从零到可用"的关键：迁移是代码，不是文档里的步骤。

## 为什么这些配置是这样

| 配置 | 原因 |
|---|---|
| `db` 只 `expose` 不 `ports` | 数据库不该暴露给宿主机/公网，只有 api 能连 |
| `api` 的 `TZ=Asia/Shanghai` | 业务按东八区自然日切分；容器默认 UTC 会让凌晨打卡算到前一天 |
| `api` 的健康检查在镜像里 | `depends_on: service_healthy` 依赖它，否则 web 会在 api 没起来时先启动 |
| `uploads` 卷 api 写、nginx 读 | 静态文件不经过 Java：省线程，也省一次代理跳转 |
| `web` 用 `output: "standalone"` | 镜像只带运行必需文件（~58MB 产物 vs 整个 node_modules） |
| `nginx` 里 `/api/v1/ai/` 单独一段 | SSE 必须 `proxy_buffering off`，且超时要放宽（批次 7） |
| 迁移跟着应用启动 | 少一个需要人记住的步骤；多副本时 Flyway 自带锁，不会重复执行 |

## 备份 / 恢复 / 演练

```bash
# 备份（自定义格式，保留 14 天）
./ops/backup/backup.sh

# 恢复演练：还原到 blog_drill，断言行数，然后删掉（默认不动生产库）
./ops/backup/restore.sh backup/db/2026-10-24.dump

# 真恢复（危险操作，必须显式指定目标库）
TARGET_DB=blog ./ops/backup/restore.sh backup/db/2026-10-24.dump
```

Windows 本地（无 Docker）也能演练：

```powershell
powershell -ExecutionPolicy Bypass -File teamblog/ops/backup/drill.ps1
```

**为什么恢复脚本默认还原到临时库**：它最常见的用法是演练。如果默认覆盖生产库，
总有一天会在错误的终端里按回车。

**为什么必须有演练**：`pg_dump` 成功不代表能还原。权限、扩展、序列、时区都可能出问题，
而这些只有真还原一次才会暴露。没演练过的备份等于没有备份。

## G5 检查清单与当前状态

| 检查项 | 状态 | 说明 |
|---|---|---|
| compose 四服务定义（api/web/db/nginx + 健康检查 + 依赖顺序） | ✅ 已写 | `compose.yaml`，结构已用 YAML 解析校验 |
| 路由：`/` → web，`/api` → api，`/uploads` → 卷 | ✅ 已写 | `ops/nginx/default.conf` |
| SSE 友好（`proxy_buffering off` + 长超时） | ✅ 已写 | 批次 7 直接可用 |
| Flyway 启动时迁移 | ✅ 已有 | 应用启动即执行，无需 migrate 服务 |
| Dockerfile（多阶段 + 非 root + 健康检查） | ✅ 已写 | api 与 web 各一份 |
| web standalone 产物 | ✅ 已验证 | `pnpm build` 产出 `.next/standalone/server.js`（57.9 MB） |
| 备份 + 恢复演练 | ✅ **已真跑** | 8 张表行数逐一对齐（users 12 / checkins 284 / point_events 284 / projects 3 / feedbacks 1） |
| `docker compose up` 实际起栈 | ✅ **已真跑** | 四个容器全部 healthy，见下 |
| 登录/发内容/打卡（经 nginx） | ✅ **已真跑** | `verify-g5.py` 14/14 通过 |

### 实测记录（2026-10-24）

```
teamblog-api-1    Up (healthy)
teamblog-nginx-1  Up (healthy)
teamblog-web-1    Up (healthy)
teamblog-db-1     Up (healthy)

GET http://localhost:8088/api/v1/health
  {"ok":true,"data":{"status":"UP","db":"UP","flyway":"1"}}
```

`verify-g5.py` 覆盖 14 项：健康检查、db 状态、flyway 版本、首页、登录、
成员列表、成果流、打卡、发布学习内容、内容回读、/uploads 路由、未登录 401。

### 起栈过程中真踩到的三个坑

1. **Docker Hub 不可达**（`auth.docker.io` 超时）→ 配 registry mirror，
   但镜像站在大 layer 上会 EOF → 最终让 Docker 走本机 Clash 代理（127.0.0.1:7897），
   30 秒拉完 node:22-alpine。
2. **环境变量名对不上**：compose 传 `DB_URL/DB_USERNAME`，应用读 `DATABASE_URL/DB_USER`。
   容器日志是"Connection to localhost:5432 refused"——因为根本没读到 URL，
   用了 application.yml 里的默认值。这就是"真跑一遍"才能发现的问题。
3. **首次部署库是空的**：生产 profile 不加载种子，于是没有任何账号，
   而创建账号需要管理员邀请 → 死锁。见下方"首启引导"。

> **为什么一定要真跑**：前两条在代码评审和 YAML 校验里都看不出来。
> G5 存在的意义就是把这类问题提前到"还没写 AI 代码"的时候。

## 首启引导（当前是手工步骤，批次 8 要补自动化）

空库部署后没有账号，需要手工建第一个管理员：

```bash
docker exec teamblog-db-1 psql -U blog -d blog -c \
  "insert into users (id, username, email, password_hash, role, total_points)
   values (gen_random_uuid(), 'admin1', 'admin1@teamblog.local',
           crypt('<你的密码>', gen_salt('bf', 12)), 'ADMIN', 0);"
```

> 这是**已知缺口**：方案里提到用 `ADMIN_BOOTSTRAP` 环境变量 + 首次登录强制改密，
> 还没实现。在那之前，新环境必须手工执行上面这条 SQL，并且立刻改掉初始密码。
## AI 服务（批次 7）

`teamblog/ai/` 是独立的 FastAPI 服务，**Nginx 直连，不经 Spring**（SSE 经 Spring 转发要占线程，
不划算）。

```
/ai/*  → ai:8000
```

| 端点 | 说明 |
|---|---|
| `GET /ai/health` | 健康检查（含 provider） |
| `POST /ai/v1/project-chat` | 项目问答，SSE 流式 |
| `POST /ai/v1/summarize` | 摘要，**仅内网**：外网经 Nginx 一律 403，需 HMAC 头 |

### 没有 API key 也能验

默认 `AI_PROVIDER=mock`：mock 会把问题回显并逐字发送，用来验证**链路**（含 Nginx 缓冲）。
接真模型只改环境变量：

```bash
AI_PROVIDER=deepseek
AI_API_KEY=sk-xxx
AI_MODEL=deepseek-chat
```

然后 `docker compose up -d ai`。

### SSE 的三个关键点

1. `proxy_buffering off`（nginx）+ `X-Accel-Buffering: no`（应用），少一个就变成"一次性输出"
2. 15 秒 `: ping` 心跳，防止中间层掐断空闲连接
3. 客户端断开后停止生成——否则关页面了 token 还在烧

### 实测（经 Nginx）

```bash
python teamblog/ai/scripts/verify_sse.py
```

```
首块到达 0.06s   总耗时 1.29s   收包 67 次（分批，不是一次性）
事件序列 delta ×120 → done
```

## 当前进度

| 批次 | 状态 |
|---|---|
| 0 地基 + CI | ✅ |
| 1 认证闭环（G1） | ✅ |
| 2 垂直切片（G2） | ✅ |
| 3 内容发布（G3） | ✅ |
| 4 打卡/积分/对账（G4） | ✅ |
| 5 部署门禁（G5） | ✅ 四容器 healthy，14 项验收 |
| 6 互动 + 后台（G6） | ✅ |
| 7 AI（G7） | 🚧 7.1 骨架 / 7.2 SSE / 7.3 摘要端点 已完成；prompt 离线评测、Spring 侧调用、前端抽屉待做 |