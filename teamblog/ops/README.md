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

| 批次 | 状态 | 证据 |
|---|---|---|
| 0 地基 + CI | ✅ | 三个 job 全绿 |
| 1 认证闭环（G1） | ✅ | 登录→刷新→登出→旧令牌 401 |
| 2 垂直切片（G2） | ✅ | 改库里的 bio，刷新即变 |
| 3 内容发布（G3） | ✅ | 发布→访客可见→下架→404，库中 status=REMOVED |
| 4 打卡/积分/对账（G4） | ✅ | 连续 7 天 +6；对账把 56 修正为 46 |
| 5 部署门禁（G5） | ✅ | 四容器 healthy；`verify-g5.py` 14/14 |
| 6 互动 + 后台（G6） | ✅ | 回复三级 →422；访客留言 → 管理员标记已读 |
| 7 AI（G7） | ✅ | 首字 1.8s；评测 20/20；摘要生成→保存→回读一致 |
| 8 硬化 | ✅（除监控） | 首启引导、HTTPS+Secure Cookie、备份排程+恢复演练、密码重置 |

测试规模：后端 **206**（权限矩阵 68 个用例）、AI 6、前端 build+lint。

### 明确不做的（不是遗漏）

| 项 | 决定 | 原因 |
|---|---|---|
| 探活/告警 | **暂不做** | 一期是学习项目，没有真实用户；需要时用 UptimeRobot 之类的免费外部探活即可（5 分钟配置，且比本地脚本更接近真实用户视角） |
| 强制改密 | 不做 | 需要"下次登录必须改密"的字段与流程，收益低于成本；当前用日志提醒 |
| 自助找回密码 | 不做 | 没有邮件服务；管理员重置已覆盖 |
| 多级评论 | 不做 | 两级已满足讨论规模，树形渲染成本高一个数量级 |
| RAG / 全站检索 | 不做 | 项目问答只拼当前项目内容，方案里明确排除 |

> 写下来的目的：**"没做"和"忘了做"是两件事**。上面每一条都是权衡后的决定，
> 将来有人问"为什么没有告警"，答案在这里，而不是靠记忆。
## 首启引导（已自动化，批次 8.1）

空库启动时，只要设置了 `ADMIN_BOOTSTRAP_PASSWORD`，应用会自动创建第一个管理员：

```bash
# .env
ADMIN_BOOTSTRAP_USERNAME=admin
ADMIN_BOOTSTRAP_PASSWORD=<一次性强密码>
```

三条规则：

| 情况 | 行为 |
|---|---|
| 库里已有用户 | **完全跳过**，不做任何修正 |
| 空库 + 有密码 | 创建管理员，日志 WARN 提醒尽快改密 |
| 空库 + 没密码 | 不创建，日志提示怎么处理 |

**为什么不给默认密码**：默认密码等于每个忘记配置的环境都有一个公开后门。

引导完成后建议把 `ADMIN_BOOTSTRAP_PASSWORD` 从环境变量里删掉——它只在空库时有意义。

实测：新建空库 `blog_bootstrap_test` → 启动 api → 自动出现 `admin/ADMIN`；
再启动一次 → 用户数不变、无任何日志动作。

## HTTPS（批次 8.2）

```bash
# 1. 生成自签证书（本地/预发用；生产换成正式证书，路径不变）
docker run --rm -v "D:\TeamBlog\teamblog\ops\nginx\certs:/certs" alpine/openssl req -x509 -nodes \
  -newkey rsa:2048 -days 365 -keyout /certs/privkey.pem -out /certs/fullchain.pem \
  -subj "/CN=localhost" -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

# 2. .env 里打开 Secure Cookie
#    COOKIE_SECURE=true

# 3. 用 TLS 覆盖文件起栈
docker compose -f compose.yaml -f compose.tls.yaml up -d
```

访问 <https://localhost:8443>（自签证书浏览器会警告，点“继续”即可）。

实测：

```
https://localhost:8443/api/v1/health  → 200 {"ok":true,"db":"UP"}
http://localhost:8088/                → 301 → https://localhost:8443/
Set-Cookie: rt=...; Secure; HttpOnly; SameSite=Lax
```

两个必须同时做的事：

- `COOKIE_SECURE=true`：否则 HTTPS 下仍会发出不带 `Secure` 的 Cookie（能用，但明文可被截）
- Nginx 传 `X-Forwarded-Proto: https`：否则应用不知道自己在 TLS 后面

`Strict-Transport-Security`（HSTS）在 `https.conf` 里是注释状态：**一旦开启，浏览器会强制该域名只走 HTTPS**，本地自签调试时会很难受。确认证书没问题后再打开。
## 备份排程（批次 8.3）

`backup` 服务跟着 compose 一起起，每天 03:10（容器时区）自动备份：

```bash
docker compose up -d backup          # 已在默认栈里，无需单独启动
docker compose logs -f backup        # 看调度情况
```

- 备份文件在卷 `backups` 的 `/backup/db/YYYY-MM-DD.dump`（自定义格式，压缩）
- 默认保留 14 天，过期的自动删除
- 同一天重复执行会覆盖（幂等），不会堆半成品

**为什么用 `sleep` 循环而不是 cron**：为了一个任务装 cron、处理时区与日志重定向，
不如一个循环——日志直接进 `docker logs`。

**为什么备份容器用 `postgres:16-alpine`**：它自带 `pg_dump`/`pg_restore`，
且版本与数据库一致（备份工具的版本必须 ≥ 服务端版本，否则可能导出失败）。

### 手动备份与恢复演练

```bash
# 立刻备份一次
docker compose run --rm -T -e RUN_ONCE=1 backup

# 恢复演练：从卷里的 dump 还原到临时库，逐表比对行数，然后删掉临时库
docker run --rm --network teamblog_default \
  -v teamblog_backups:/backup \
  -v "$PWD/../backup:/scripts:ro" \
  -e PGPASSWORD=<密码> -e DUMP=/backup/db/2026-10-24.dump \
  postgres:16-alpine sh /scripts/restore-drill.sh
```

实测（2026-10-24）：

```
[backup] 开始备份 blog@db -> /backup/db/2026-10-24.dump
[backup] 完成：36.0K

[drill] 行数比对
        users            源      1  还原      1  OK
        checkins         源      1  还原      1  OK
        point_events     源      4  还原      4  OK
        projects         源      3  还原      3  OK
        learning_logs    源      8  还原      8  OK
        comments         源      0  还原      0  OK
        feedbacks        源      0  还原      0  OK
[drill] 通过：备份可以还原，行数完全一致
```

**没演练过的备份等于没有备份**：`pg_dump` 成功不代表能还原——权限、扩展、序列、
时区都可能出问题，而这些只有真还原一次才会暴露。
## 忘记密码怎么办（批次 8.4）

一期没有邮件服务，所以**没有"自助找回"**。唯一的恢复路径是管理员重置。

前端：后台 → 成员 → 「重置密码」→ 输入新密码。

或者命令行：

```bash
curl -k -X POST https://localhost:8443/api/v1/admin/users/<用户ID>/password \
  -H "Authorization: Bearer <管理员 accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"newPassword":"NewPass#2026"}'
```

**重置后对方所有已登录设备立刻失效**（`tokenVersion` +1），必须用新密码重新登录。
这既是安全要求，也是"确实重置了"的证明——如果旧会话还能用，说明重置没生效。

密码规则（`PasswordRules`）：

| 规则 | 原因 |
|---|---|
| 至少 8 位 | 下限 |
| 最多 72 **字节** | BCrypt 只取前 72 字节，超出静默截断 |
| 不能等于用户名 | 最常见的弱密码 |

按字节而不是字符：一个汉字 3 字节，25 个汉字就 75 字节了。

成员自己也可以改密码（`POST /api/v1/me/password`），**必须提供旧密码**——
否则一个被盗用的 access token 就能改密码、彻底接管账号。

实测：

```
注册 pwdemo → 旧密码登录 成功
管理员重置 → 200
旧令牌访问 /me → 401        ← 会话立刻失效
新密码登录 → 200
太短密码 → 400
自己改密码（旧密码正确）→ 200
自己改密码（旧密码错误）→ 401
```
## AI 分档与"资料过少"守卫（2026-10）

### 两档模型

| 档位 | 模型 | 每日额度 | 回答上限 | 思考模式 |
|---|---|---|---|
| 访客（未登录） | `deepseek-flash` | 8 次/IP | 300 字 | 关 |
| 成员（带 token） | `deepseek-flash` | **不限**（`0`） | 800 字 | 关（可开） |

**"不限"的写法**：额度 <= 0 表示不限，只记数不拦。不用写 `999999` 那种魔法大数——
后来人看不出那是"无限"还是"真的想要九十九万"。

> 成员不限额度意味着**没有成本上限**：被刷时损失只受 DeepSeek 账户余额约束。
> 建议在 DeepSeek 后台设一个充值/消费上限兜底。

**怎么区分**：前端在 `POST /ai/v1/project-chat` 带 `Authorization: Bearer <accessToken>`，
AI 服务用**与 Spring 共享的 JWT 密钥**验签、读出 `sub`。

> 这不是"AI 做用户认证"（方案 §1.2 明确禁止）：AI 不查库、不管会话，只验证一个已签好的声明。
> 验签失败按匿名处理——不报 401，因为匿名本来就允许访问。

额度键也不同：匿名按 IP，成员按用户 id（**换个 IP 也刷不动**）。

### 为什么关掉思考模式

DeepSeek 的 `deepseek-v4-pro` 默认开思考（`effort=high`），首字延迟从 1s 涨到 **9s**。
我们做的是"只依据资料回答"的检索式问答，思考带来的增益不明显。

实测（同一问题）：

```
匿名 flash         0.95 – 1.11s
成员 pro（关思考）  1.40 – 1.42s
成员 pro（开思考）  2.40 – 9.21s   ← 默认值
```

想换回高质量模式：`.env` 里 `AI_THINKING=enabled`，并把 `AI_MEMBER_MAX_TOKENS` 提到 2000+。

### 资料过少时不调模型

如果一个项目的"描述 + 正文"合计少于 20 字（`AI_MIN_USABLE_CONTENT_CHARS`），
直接返回固定提示，**不调模型**：

```
这个项目的资料还很少（标题、描述、正文看起来都是占位内容），没法基于资料回答。
可以让作者补充项目介绍后再来问；或者换个问题，比如它解决了什么问题、用了什么技术。
```

原因：资料是 `test` 这种占位内容时，模型只能反复说"资料里没有写…资料中仅显示…"，
既烧 token 又显得蠢。这是**产品层**的守卫，不是 prompt 能救的。

### 空回答也要报错

思考模式偶尔会把 `max_tokens` 全花在推理上，导致一个字的正文都没输出。
静默返回空会让前端出现"空气泡"，所以现在会发 `event: error` + `EMPTY_ANSWER`，
前端提示"这次没答上来，请再问一次"。
### 两档的回答策略（2026-10 调整）

| 档位 | 行为 |
|---|---|
| **成员**（带 token） | **不加限制**：通用知识、写代码、给建议、闲聊都能问 |
| **访客**（匿名） | 仍限制在项目资料内；通用概念可以解释，但必须标注「（通用说明，非项目资料）」 |

**唯一保留的一条约束**：涉及"本项目的具体事实"（用了什么技术、怎么部署）时以资料为准，
资料没有就说没有——这不是限制能力，是防止 AI 替项目编造技术栈。

实测对比（同一个问题）：

```
问：Spring Boot 3 是什么
访客：（通用说明，非项目资料）Spring Boot 3 是 Spring 官方推出的 Java 开发框架…
成员：Spring Boot 3 是 Java 生态中一个成熟的后端应用开发框架…

问：帮我写个 Python 快速排序
访客：这个问题不属于项目相关的问题，也不属于通用技术概念的解释范畴…（拒答）
成员：没问题，下面是一个标准的 Python 快排实现：```python def quicksort(arr)…```
```

实现：`app/prompts.py` 里两套模板（`PROJECT_CHAT_TEMPLATE` / `MEMBER_CHAT_TEMPLATE`），
由 `identity.logged_in` 选择。两档差异由 `tests/test_prompts.py` 钉住，
改错了不会从行为上悄悄溜过去。

> 为什么访客仍受限：公开链接被谁拿到都可能，不限制就等于送一个免费通用 ChatGPT（花你的 token）。