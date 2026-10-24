# api · Spring Boot 后端

Spring Boot 4.1.2 + Java 17 + PostgreSQL 16 + Flyway + Spring Data JPA。

## 三分钟跑起来

前置：JDK 17、PostgreSQL 16 已启动，且已有 `blog`（开发）与 `blog_test`（测试）两个库。

```bash
# 1. 建库（首次；可重复执行）
psql "postgresql://blog:blog_dev_pw@localhost:5432/blog" -f ../ops/init/01-roles.sql
psql "postgresql://blog:blog_dev_pw@localhost:5432/blog" -f ../ops/init/02-test-db.sql

# 2. 启动（dev profile 会额外加载种子数据）
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 3. 验证
curl localhost:8080/api/v1/health
# {"ok":true,"data":{"status":"UP","db":"UP","flyway":"1"},"error":null}
```

```bash
# 测试（连 blog_test，不污染开发库；每个用例结束回滚）
./mvnw verify
```

> `jwt.secret` 在 dev profile 里有本机默认值；**其它环境必须设环境变量 `JWT_SECRET`**
> （≥32 字节），否则启动直接失败（S-07）。

## 目录结构

按**业务**分包，不按技术分层。一个功能的所有代码在同一个包里：

```
com/teamblog
├─ ApiApplication.java
├─ auth/        认证：JWT 签发校验、过滤器、当前用户主体
│   ├─ JwtService / JwtFilter / JwtProperties / AuthPrincipal / ErrorResponder
├─ user/        用户：实体、仓储、服务、端点、DTO
│   ├─ User / Role / UserRepository / UserService / UserController / UserResponse
├─ config/      配置：安全链、密码编码器、时钟
│   ├─ SecurityConfig / PasswordConfig / ClockConfig
├─ common/      横切：统一信封、错误码、异常、工具
│   ├─ ApiResponse / ErrorCode / BusinessException / GlobalExceptionHandler / UuidUtils
└─ health/      健康检查
    └─ HealthController
```

### 与"按层分包"的对照

如果你习惯 `controller/ service/ mapper/` 那种结构，对照关系如下（苍穹外卖式布局）：

| 层 | 按层布局 | 本项目在哪 |
|---|---|---|
| Controller | `controller/` | 各业务包内，如 `user/UserController` |
| Service | `service/` + `service/impl/` | 各业务包内，如 `user/UserService`（不抽接口，J-26） |
| DAO | `mapper/` + XML | **不存在**：`user/UserRepository` 由 Spring Data 生成实现 |
| 实体 | `pojo/entity/` | 各业务包内，如 `user/User` |
| DTO / VO | `pojo/dto/`、`pojo/vo/` | 各业务包内，如 `user/UserResponse` |
| 配置 | `config/` | `config/` |
| 拦截器 | `interceptor/` | `auth/JwtFilter`（Spring Security 过滤器） |
| 统一返回/异常 | `common/result`、`common/exception` | `common/ApiResponse`、`common/ErrorCode` |

**为什么不用按层布局**：本项目最终 60~80 个类、8 个批次。按层会让 `controller/` 平铺
几十个文件，改一个功能要横跨四个目录。按业务分包则"一个功能一个目录"，新增功能不修改
既有目录。详见 `实施方案/07-开发规范.md` 的 J-01。

## 分层职责（不可越界）

```
Controller  只做参数接收与 DTO 转换，不写业务 if（J-05）
    │       只能依赖 Service
Service     全部业务规则与事务边界（@Transactional 只加在这里）
    │       只能依赖 Repository 与其它 Service
Repository  只做查询，不含业务判断
    │
Entity      不出 Service 层；对外一律 DTO（J-36）
```

`§13` 计划用 ArchUnit 把这三条变成测试：Controller 不得依赖 Repository、Entity 不得
出现在 Controller。

## 常见错误

| 现象 | 原因 | 处理 |
|---|---|---|
| `JAVA_HOME is not defined correctly` | 机器级 JAVA_HOME 指向不存在的目录 | 改指向 JDK 17 安装目录 |
| 启动报 `jwt.secret 至少 32 字节` | `JWT_SECRET` 未设置或太短 | 设一个 32 字节以上的随机串 |
| 启动报 `Schema-validation: missing column` | 实体字段与 Flyway 迁移不一致 | 改实体；**不要**改已执行的迁移（D-02） |
| `mvn verify` 报 `applied migration not resolved locally: seed dev` | 用了 dev 库做测试 | 确认走 `test` profile（连 `blog_test`） |
| 接口全 401 | 少了 `Authorization: Bearer <token>` | 检查请求头 |
| 队员 clone 后跑不起来 | 没建 `blog_test` 库 | `psql ... -f ops/init/02-test-db.sql` |

## 约定

编码规范见 `实施方案/07-开发规范.md`；模块讲解见 `docs/learning/`。
