# TeamBlog

团队技术博客平台。面向小型技术团队的成果发布与学习记录系统，包含成员认证、内容发布、评论互动、打卡积分、AI 问答与运营后台六个模块。

当前状态：**内测**。功能与数据结构仍可能调整。

---

## 1. 功能范围

系统按角色划分权限，功能矩阵如下。

| 能力 | 访客 | 成员 | 管理员 |
| --- | :---: | :---: | :---: |
| 浏览成果、学习内容、成员列表、打卡热力图 | ✓ | ✓ | ✓ |
| 就指定项目向 AI 提问（流式返回） | ✓（受限额度） | ✓（不限额度） | ✓ |
| 提交反馈 | ✓ | ✓ | ✓ |
| 发布成果 / 学习内容 | — | ✓ | ✓ |
| 评论与回复（两级） | — | ✓ | ✓ |
| 每日打卡与积分累计 | — | ✓ | ✓ |
| 生成内容摘要 | — | ✓ | ✓ |
| 修改本人密码 | — | ✓ | ✓ |
| 邀请注册、调整角色、重置他人密码 | — | — | ✓ |
| 内容下架、反馈处理、积分对账 | — | — | ✓ |

未登录用户不具备写入权限；注册采用邀请制，不开放自主注册。

---

## 2. 系统架构

系统由四个应用服务与两项基础设施组成，统一由 Nginx 对外提供入口。

```
                         ┌──────────────┐
         HTTPS :443 ────► │    Nginx     │
                         └──┬────┬────┬─┘
                    /       │    │    │      /api/
              ┌─────────────┘    │    └─────────────┐
              ▼                  ▼ /ai/             ▼
       ┌────────────┐   ┌──────────────┐   ┌──────────────┐
       │  Web       │   │  AI Service  │◄──│  API         │
       │  Next.js   │   │  FastAPI     │   │  Spring Boot │
       └────────────┘   └──────────────┘   └──────┬───────┘
                                 │ HMAC           │
                                 └── 只读视图 ────┤
                                                 ▼
                                          ┌─────────────┐
                                          │ PostgreSQL  │
                                          └──────┬──────┘
                                                 │
                                          ┌──────┴──────┐
                                          │  备份服务   │
                                          └─────────────┘
```

关键设计决策：

1. **AI 服务独立部署，不经 API 转发。** 问答采用 SSE 长连接，由 API 转发将长期占用应用服务器线程池。
2. **Nginx 直接路由 AI 服务，并关闭响应缓冲。** 缓冲开启会使流式输出退化为一次性返回。
3. **AI 服务仅访问只读视图 `v_projects_public`。** 权限边界收敛在数据库视图层，AI 服务不接触业务表。
4. **内部摘要接口由 API 通过容器网络调用并附加 HMAC 签名。** 该端点在外网被 Nginx 直接拒绝（403）。
5. **备份服务随编排文件一同部署。** 更换部署环境时无需重新配置定时任务。

---

## 3. 技术选型

| 层次 | 技术 |
| --- | --- |
| 前端 | Next.js 15（App Router）、React 19、Tailwind CSS 4、shadcn/ui |
| 后端 | Spring Boot 4.1.2、Java 17、Spring Security、Spring Data JPA |
| 数据 | PostgreSQL 16、Flyway（版本化迁移） |
| AI | FastAPI、DeepSeek API（OpenAI 兼容协议）、SSE |
| 部署 | Docker Compose、Nginx（TLS 终止）、Let's Encrypt 或自签证书 |

---

## 4. 部署

### 4.1 前置条件

- Docker Desktop（Windows 需启用 WSL2）
- 可用端口：8088（HTTP）、8443（HTTPS）

### 4.2 启动

双击仓库根目录的 `start.bat`，或在命令行执行：

```powershell
powershell -ExecutionPolicy Bypass -File teamblog/ops/compose/up.ps1
```

`start.bat` 会依次启动容器栈，并询问是否开启公网隧道。停止服务双击 `stop.bat`（数据保留在 Docker 卷中）。

该脚本依次完成：引擎检测、`.env` 生成（随机数据库密码与 JWT 密钥）、TLS 证书生成、镜像构建、容器启动、健康检查。

服务启动后访问 `https://localhost:8443`。首次部署使用自签证书，浏览器会提示证书不受信任，属预期行为。

### 4.3 首次管理员

系统在空数据库启动时依据环境变量创建初始管理员，配置项为 `ADMIN_BOOTSTRAP_USERNAME` 与 `ADMIN_BOOTSTRAP_PASSWORD`。

未设置密码时不会创建任何账号。此为有意设计：内置默认密码等同于在未配置的环境中预留公开入口。

### 4.4 本地开发

```bash
# 后端（默认 8080，连接本机 PostgreSQL）
cd teamblog/api && ./mvnw spring-boot:run

# 前端（默认 3000）
cd teamblog/web && pnpm dev

# AI 服务（默认 8000）
cd teamblog/ai && uvicorn app.main:app --reload --port 8000
```

前端通过 `API_ORIGIN` 与 `AI_ORIGIN` 两个环境变量配置代理目标。

### 4.5 配置项

主要环境变量（完整清单见 `teamblog/ops/compose/.env.example`）：

| 变量 | 说明 |
| --- | --- |
| `POSTGRES_PASSWORD` | 数据库密码 |
| `JWT_SECRET` | 令牌签名密钥，前后端共用（AI 服务仅用于校验签名） |
| `COOKIE_SECURE` | 刷新令牌 Cookie 是否附加 `Secure`，HTTPS 部署时应为 `true` |
| `AI_PROVIDER` | `mock` 或 `deepseek` |
| `AI_API_KEY` | 模型服务密钥 |
| `AI_ANON_DAILY_LIMIT` | 访客每日提问上限（按 IP） |
| `AI_MEMBER_DAILY_LIMIT` | 成员每日提问上限（按用户），`0` 表示不限 |
| `AI_THINKING` | 是否启用模型思考模式，`disabled` 可显著降低首字延迟 |
| `INTERNAL_HMAC_SECRET` | 内部摘要接口的签名密钥 |

---

## 5. 验证

| 命令 | 覆盖范围 |
| --- | --- |
| `python teamblog/ops/compose/verify-g5.py` | 部署链路 14 项（健康检查、登录、读写、静态资源、鉴权） |
| `python teamblog/ai/scripts/verify_g7.py` | AI 流式链路（首字延迟、事件类型、内容准确性） |
| `python teamblog/ai/evals/run_eval.py` | 提示词质量 22 条用例 |
| `python teamblog/ai/scripts/probes.py` | 访客与成员两档回答差异 |

`verify-g5.py` 的账号密码通过 `G5_USER`、`G5_PASSWORD` 环境变量传入。

### 5.1 测试与统计

| 项目 | 数量 |
| --- | --- |
| 后端测试用例 | 206（含权限矩阵 63 项，覆盖全部端点与三种身份） |
| AI 服务测试用例 | 15 |
| 提示词评测用例 | 22（可达性 9、资料缺失 6、通用概念 2、越权与注入 5） |
| OpenAPI 路径 | 29 |
| 学习文档 | 15 篇 |

持续集成在推送至 `main` 时执行三项检查：后端测试、前端构建、接口契约漂移检测。

---

## 6. 目录结构

```
teamblog/
├── api/        Spring Boot 服务
├── web/        Next.js 应用
├── ai/         FastAPI 服务、提示词评测
└── ops/        编排文件、Nginx 配置、备份与恢复演练
docs/
├── api/        OpenAPI 契约（前端类型的唯一来源）
└── learning/   实现说明文档
实施方案/        需求定稿与实施计划
```

---

## 7. 已知限制

以下项目为当前版本的范围外内容，非缺陷。

| 项目 | 现状 | 影响 |
| --- | --- | --- |
| 监控与告警 | 未实现 | 服务异常需由使用者反馈，无主动通知 |
| 自助找回密码 | 未实现 | 依赖管理员重置 |
| 首次登录强制改密 | 未实现 | 仅通过日志提示 |
| 数据备份异地存储 | 未实现 | 备份位于本机卷，宿主机故障将同时丢失数据与备份 |
| 上传文件内容检测 | 仅校验文件头 | 未做图片重编码与恶意内容扫描 |
| 多轮对话 | 未实现 | 每次提问独立，不携带上下文 |
| 检索增强 | 未实现 | 仅拼接当前项目内容，无全站检索 |

---

## 8. 文档

| 文档 | 内容 |
| --- | --- |
| [实现说明（15 篇）](docs/learning/README.md) | 各模块的设计取舍与实现细节，含自测题 |
| [部署与运维](teamblog/ops/README.md) | 启动、HTTPS、备份恢复、AI 策略 |
| [API 契约](docs/api/team-blog-api.openapi.yaml) | OpenAPI 3.1 |
| [实施方案](实施方案/) | 需求定稿、实施方案、开发规范、实施顺序 |

---

## 9. 许可

本项目为学习用途，未声明开源许可证。
