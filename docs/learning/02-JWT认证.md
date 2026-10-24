# 02 · JWT 认证（零基础版）

> **前置**：[00-零基础必读](./00-零基础必读.md) §1（HTTP/状态码）、§2（Cookie）、§4（编码/哈希）、§5（无状态）。
> **本篇目标**：读完能说清"服务器凭什么知道我是谁"，并能自己写出签发、校验、过滤器三段。
> **对应代码**：`auth/{JwtService,JwtFilter,JwtProperties}`, `config/SecurityConfig`, `common/{ErrorCode,BusinessException,GlobalExceptionHandler}`

---

## 0. 先讲人话

HTTP 是**没有记忆**的：你发两个请求，服务器不知道它们来自同一个人。

那登录之后，服务器怎么知道"这个请求是谁发的"？

你在 JavaWeb 里的答案是 Session：

```java
session.setAttribute("user", user);      // 服务端记一笔
User u = (User) session.getAttribute("user");  // 之后每次去取
```

这个方案的问题，只有在你**加第二台服务器**的时候才会暴露：

```
第一台：请求 A 登录 → Session 存在第一台
第二台：请求 B 带着同样的 Cookie → 第二台没有这个 Session → 你"掉线"了
```

要解决就得让两台共享 Session（通常要引 Redis）。于是有了另一种思路：

**不存了，让客户端自己带着身份，并且带上"服务器签过的章"。**

这就是 JWT。

---

## 1. 先理解问题

### 1.1 一个"没法伪造的身份卡"需要什么

如果我只是在请求里写 `"我是 admin1"`，显然不行——谁都能写。

需要的是一张**我改不了、但服务器能验**的东西。思路很朴素：

```
服务器有只有它自己知道的 secret
服务器对"我是 admin1"这串字算一个签名，附在后面
你改了任何一个字，签名就对不上
```

这就是 JWT。

### 1.2 JWT 长什么样

```
eyJhbGciOiJIUzI1NiJ9 . eyJpc3MiOiJ0ZWFtYmxvZyIsInN1YiI6ImFiYyJ9 . 3f9c2a...
└────── header ──────┘ └──────────── payload ────────────┘ └─ signature ─┘
```

- **header**：`{"alg":"HS256"}` —— 用什么算法
- **payload**：业务声明，比如 `{"sub":"用户id","role":"MEMBER","tv":0,"exp":...}`
- **signature**：`HMAC-SHA256(header + "." + payload, secret)`

三段都是 **Base64URL 编码**（见 00-§4：编码不是加密）。

> **必须记住的第一件事**：任何人都能把 payload 解开看到内容，只是改不了。
> 所以 JWT 里绝不能放密码、手机号、内部备注。

### 1.3 为什么改一个字就失效

签名是对 `header.payload` 整体算出来的。改了 payload 里任何一位，重算的签名就和携带的
签名不一致 → 验签失败。

想伪造，就必须知道 `secret`。这也说明：**secret 泄漏 = 任何人都能签出管理员 token**。

---

## 2. 设计：三个问题

### 2.1 签什么？—— 只放不可变、且一定要查库确认的东西

| 放什么 | 问题 |
|---|---|
| 用户名 | 能改。改了之后 token 里的名字永远是旧的 |
| 邮箱 | 同上，而且敏感（payload 谁都能看） |
| **用户 id（UUID）** | 不可变、无意义、不泄露信息 ✓ |
| **role** | 只作展示与日志，权限判断仍以库为准 |
| **tokenVersion** | 撤销的关键（见 2.3） |

### 2.2 放哪？—— `Authorization: Bearer <token>`

浏览器**不会自动携带**这个头（不像 Cookie）。这带来两个好处：

1. 天然免疫 CSRF：别的网站诱导你点链接，也不会自动带上它
2. 跨域时由前端显式控制

### 2.3 怎么失效？—— 这是 JWT 唯一的大坑

访问令牌是**无状态**的：服务器不存，所以**收不回来**。

用户改密码了，旧 token 还有 30 分钟有效期，照样能用。

解法：签发时把用户当前的 `token_version` 写进 payload：

```
token 里：tv = 3
每次请求：查库拿到当前 token_version，比对
  相等 → 放行
  不等 → 401 SESSION_REVOKED
```

改密码时 `token_version++` 变成 4，所有 `tv=3` 的 token 立刻作废。

**代价**：每次请求多一次按主键的 `select`（微秒级）。
**换来**：改密码/降级/移除用户**立即生效**。

> 这个查库不是浪费，它是"撤销能力"的价格。不查库，就只能等 token 自然过期。

---

## 3. 代码逐行

### 3.1 `JwtProperties`：配置就位，缺了就崩

```java
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl,
                            String issuer, boolean cookieSecure) {
    public JwtProperties {
        if (secret != null && secret.getBytes(UTF_8).length < 32) {
            throw new IllegalArgumentException("jwt.secret 至少 32 字节……");
        }
    }
}
```

**为什么不给 secret 默认值**：

```
本地开发：有默认值 → 一切正常
上线时：忘了配环境变量 → 用的是仓库里那个"开发密钥"
结果：看过仓库的人都能签出管理员 token
```

所以基座配置写 `${JWT_SECRET:}`（默认空串），校验器发现太短直接抛异常——
**让配置错误在启动时暴露**。

**为什么至少 32 字节**：HMAC-SHA256 的密钥强度由长度决定。8 位密码当密钥，等于没签名。

### 3.2 `JwtService`：签发与校验

```java
public String issueAccessToken(User user) {
    Instant now = clock.instant();
    return JWT.create()
            .withIssuer(properties.issuer())
            .withSubject(user.getId().toString())
            .withClaim("role", user.getRole().name())
            .withClaim("tv", user.getTokenVersion())
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plus(properties.accessTtl())))
            .sign(algorithm);
}
```

- `withSubject`：放用户 id
- `withIssuer`：签发者。校验时也验它，防止"别处用同一个密钥签的 token"混进来
- `withExpiresAt`：30 分钟后过期
- **时间来自注入的 `Clock`**，不是 `Instant.now()`——这样测试能造出"签发即过期"的 token（见 §7）

```java
public AccessTokenClaims verify(String token) {
    DecodedJWT decoded = verifier.verify(token);      // 签名/issuer/过期 都在这一步校验
    Integer tv = decoded.getClaim("tv").asInt();
    return new AccessTokenClaims(
            UUID.fromString(decoded.getSubject()),
            Role.valueOf(decoded.getClaim("role").asString()),
            tv);
}
```

`verify` 抛 `JWTVerificationException`（签名不对、过期、issuer 不符、载荷畸形），
调用方只需要 catch 一种异常。

### 3.3 `JwtFilter`：请求进门的第一道

```java
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
    String header = request.getHeader("Authorization");

    // ① 没带 token：放行，交给后面的授权规则决定要不要登录
    if (header == null || !header.startsWith("Bearer ")) {
        chain.doFilter(request, response);
        return;
    }

    // ② 验签失败：直接 401，不能继续往下走
    AccessTokenClaims claims;
    try {
        claims = jwtService.verify(header.substring(7));
    } catch (RuntimeException ex) {
        errorResponder.write(response, ErrorCode.UNAUTHENTICATED);
        return;
    }

    // ③ 查库：用户还在吗？token 版本还对得上吗？
    User user = userRepository.findById(claims.userId())
            .filter(u -> !u.isDeleted())
            .orElse(null);
    if (user == null) { 401 UNAUTHENTICATED; return; }
    if (user.getTokenVersion() != claims.tokenVersion()) { 401 SESSION_REVOKED; return; }

    // ④ 认证通过：把身份放进上下文，供后面的代码取用
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(new UsernamePasswordAuthenticationToken(
            new AuthPrincipal(user.getId(), user.getUsername(), user.getRole()),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    SecurityContextHolder.setContext(context);

    try {
        chain.doFilter(request, response);
    } finally {
        SecurityContextHolder.clearContext();     // ⑤ 必须清理
    }
}
```

五个点，每个都可以单独讲一页：

**① 没有 token 不是错误。** 健康检查要能匿名访问，所以过滤器不拦。"这个端点要不要登录"
是 `SecurityConfig` 的事——职责分开。

**② 验签失败必须终止。** 如果放行，下游会把它当匿名请求，用户明明带了个坏 token，
却收到"权限不足"这种误导性错误。

**③ 必须查库。** 只验签名的话，改完密码旧 token 还能用 30 分钟。查库才能让撤销立即生效。

**④ 放进 `SecurityContext`。** 之后 Controller 里 `@AuthenticationPrincipal AuthPrincipal p`
就能直接拿到当前用户，不用再解析一遍 token。

**⑤ `finally` 里清理。** Tomcat 用线程池：处理完 A 的线程可能马上去处理 B。
不清理，B 就继承了 A 的身份——**最严重的越权 bug，而且极难复现**。

> 为什么这个类**不加 `@Component`**：Spring Boot 会把容器里所有 `Filter` 类型的 Bean
> 自动注册到 Servlet 容器，那样它会在安全过滤链**之外**再跑一遍，顺序不可控。
> 所以由 `SecurityConfig` 手动 `new` 出来，只挂进链。

### 3.4 `SecurityConfig`：谁能进哪个门

```java
http
    .csrf(disable)                    // 无 Cookie 会话，CSRF 不适用
    .formLogin(disable).httpBasic(disable)
    .sessionManagement(STATELESS)     // 不创建 HttpSession
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/v1/health", "/actuator/health").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
        .anyRequest().authenticated())        // 默认拒绝，再逐个开口子
    .exceptionHandling(ex -> ex
        .authenticationEntryPoint((req, res, e) -> errorResponder.write(res, UNAUTHENTICATED))
        .accessDeniedHandler((req, res, e) -> errorResponder.write(res, FORBIDDEN)))
    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
```

**为什么默认 `authenticated()` 而不是默认放行**：漏配一个端点的后果，会从"忘了加权限"
变成"完全公开"。默认拒绝，再逐个开口子——这是安全领域唯一可扩展的做法。

**过滤链的真实顺序**（简化）：

```
Tomcat
 └─ Security 过滤链
      ├─ SecurityContextHolderFilter     准备一个空上下文
      ├─ JwtFilter                       验身份，填上下文   ← 我们插在这
      ├─ ExceptionTranslationFilter      接住后面的"拒绝"
      └─ AuthorizationFilter             读配置，判断能不能进
 └─ DispatcherServlet → Controller
```

### 3.5 `ErrorResponder`：为什么 `@RestControllerAdvice` 管不到 401

`GlobalExceptionHandler` 只处理**进入 Controller 之后**抛的异常。401/403 发生在过滤器链里，
那时还没有 Controller，所以只能自己写响应。

三处都要写（过滤器、EntryPoint、AccessDeniedHandler），所以抽了一个 `ErrorResponder`，
保证信封格式唯一。

---

## 4. 一次带 token 的请求

```
t=0ms     浏览器发 GET /api/v1/me
t=0.1ms   Authorization: Bearer eyJhbGci...
t=0.2ms   JwtFilter 取出 token
t=0.5ms   verify()：验签名、issuer、exp —— 全过
t=0.6ms   findById(claims.userId) → 查到用户
t=0.7ms   tokenVersion 比对：库里 0，token 里 0 → 通过
t=0.8ms   放进 SecurityContext，继续 chain
t=1ms     Controller 里 @AuthenticationPrincipal 拿到 principal
t=2ms     返回 {"ok":true,"data":{...}}
```

对比**改密码之后**：

```
t=0.7ms   tokenVersion 比对：库里 1，token 里 0 → 不等
t=0.8ms   返回 401 {"error":{"code":"SESSION_REVOKED"}}
```

这就是"撤销"能立即生效的全部机制。

---

## 5. 五个容易写错的细节

### 5.1 以为 payload 是加密的

不是。它是 Base64URL，任何人贴到 jwt.io 就能看到内容。

**推论**：不放敏感信息；不把 role 当安全依据（必须以库为准）。

### 5.2 `JwtFilter` 加了 `@Component`

Spring Boot 会自动把 `Filter` Bean 注册到 Servlet 容器。结果是它跑了两次：一次在安全链外、
一次在链内。轻则重复执行，重则上下文被提前清掉，身份丢失。

### 5.3 忘了 `clearContext()`

见 §3.3 ⑤。这个 bug 的表现是"偶发串号"——用户 A 看到 B 的数据，且无法稳定复现。

### 5.4 把"token 过期"和"没权限"混成一个 401

- **401** = 我不知道你是谁（没带 token / 坏了 / 过期）
- **403** = 我知道你是谁，但你不能做这个

前端据此分流：401 跳登录页；**403 不能跳登录**，否则用户会陷入"我明明登录了却让我登录"
的死循环。

### 5.5 `secret` 用了默认值

见 §3.1。默认值会跟着代码上线，等于公开密钥。

---

## 6. 反面教材

| 写法 | 后果 |
|---|---|
| 把用户密码或手机号放进 payload | 谁都能解出来（Base64 不是加密） |
| 只验签名不查库 | 改密码后旧 token 仍有效 30 分钟 |
| `JwtFilter` 加 `@Component` | 过滤链里跑两遍，顺序不可控 |
| `finally` 里不清理上下文 | 线程复用时串号，A 的身份带给 B |
| 401/403 统一返回 401 | 前端无法区分"重新登录"和"没权限" |
| secret 给默认值 / 太短 | 任何人都能伪造管理员 token |
| 用 `Instant.now()` 不用 `Clock` | 过期逻辑无法测试，只能等 |

---

## 7. 测试怎么写

### 7.1 单测：不启容器，测签名

```java
@Test
void 篡改任意一位_签名校验失败() {
    String token = jwtService.issueAccessToken(user);
    String tampered = flipOneCharInPayload(token);
    assertThatThrownBy(() -> jwtService.verify(tampered))
            .isInstanceOf(JWTVerificationException.class);
}
```

### 7.2 过期测试：不要 `sleep`

```java
// 用一个"31 分钟前的时钟"来签发 —— token 生下来就过期了
Instant past = Instant.now().minus(Duration.ofMinutes(31));
JwtService pastIssuer = new JwtService(properties, Clock.fixed(past, ZoneOffset.UTC));
String expired = pastIssuer.issueAccessToken(user);

assertThatThrownBy(() -> jwtService.verify(expired)).isInstanceOf(JWTVerificationException.class);
```

不用等 30 分钟，也不依赖机器快慢——**这是注入 Clock 最直接的收益**。

### 7.3 集成测试：为什么必须走 MockMvc

单测只能证明"签名对不对"，证明不了：

- 过滤器有没有挂进链
- 失败时谁写的响应、状态码是什么
- 信封格式对不对
- 改密码后旧 token 是不是真的失效

```java
mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.username").value("member1"));
```

---

## 8. 动手练习

1. 写 `JwtProperties`，secret 短于 32 字节时抛异常
2. 写 `JwtService.issue` / `verify`，claims 放 `sub/role/tv/iss/exp`
3. 写 `JwtFilter`：无 token 放行、坏 token 401、tv 不匹配 401、成功则填上下文
4. 写 `SecurityConfig`：默认 authenticated + 白名单 + 401/403 信封
5. 写 5 个单测（签发/篡改/换密钥/过期/issuer 不符）
6. 写 5 个集成测试（无 token/有效/篡改/改密码/软删）

---

## 9. 自测题

1. JWT 的 payload 是加密的吗？这对"能不能放手机号"意味着什么？
2. 为什么改了 payload 里一个字符，服务端就能发现？
3. token 里已经有 `role` 了，为什么每次请求还要查库？
4. 401 和 403 分别由谁决定？前端看到 403 该跳登录吗？
5. `JwtFilter` 为什么不能加 `@Component`？
6. 用户改密码后，旧 token 为什么立刻失效？去掉查库会怎样？
7. 为什么 `SecurityContextHolder.clearContext()` 必须写在 `finally`？
8. 为什么过期测试不用 `Thread.sleep`？

<details>
<summary>答案</summary>

1. 不加密，只是 Base64URL，谁都能解开。所以不能放敏感信息。
2. 签名是 `HMAC-SHA256(header.payload, secret)`；payload 变一位，重算的签名就对不上。
3. token 里的 role/tv 是签发那刻的快照；改角色/改密码后快照过期，必须以数据库为准。
4. 都由 `ExceptionTranslationFilter` 在 `AuthorizationFilter` 拒绝后判断：匿名 → 401，已登录无权限 → 403。403 不要跳登录。
5. Spring Boot 会自动把 Filter Bean 注册到 Servlet 容器，导致它在安全链之外再跑一遍，顺序不可控。
6. 改密码时 `tokenVersion++`，旧 token 的 tv 与库里不一致 → 401。去掉查库就只能等过期。
7. Tomcat 线程池复用线程；不清理会让下一个请求继承上一个用户的身份（越权）。
8. 注入可控时钟后可以"签发即过期"，测试是确定性的；sleep 既慢又不稳定。

</details>

---

## 10. 术语

| 名词 | 一句话 |
|---|---|
| JWT | 带签名的通行证，格式 `头.载荷.签名` |
| claims | JWT 载荷里的键值对（`sub`、`exp`、自定义字段） |
| `sub` | subject，这里指用户 id |
| HS256 | 用同一个密钥签名和验签（对称） |
| Bearer | 一种携带凭据的方式：`Authorization: Bearer <token>` |
| 无状态 | 服务端不保存会话，只验签名 |
| 过滤器链 | 请求进入 Controller 前依次经过的一串过滤器 |
| SecurityContext | 本次请求的"当前用户"存放处 |
| AuthenticationEntryPoint | 未认证时的出口（写 401） |
| AccessDeniedHandler | 已认证但无权限时的出口（写 403） |
