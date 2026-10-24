# 前置 · Java 注解与语法速查（本项目已用到的）

> **读者前提**：学过 Java 基础 + JavaWeb（Servlet/JSP/JDBC），但没怎么用过注解、泛型的高级写法、`record`、`Optional`、Lambda/Stream。
> **本文范围**：`teamblog/api` 里**真正出现过**的注解与语法。不追求大全，追求"看到能读懂、写的时候想得起来"。
> **配套**：[01-用户实体层.md](./01-用户实体层.md) 讲的是"为什么这么设计"，本文讲的是"这些符号是什么意思"。

---

## 0. 先把两个词分开：注释 ≠ 注解

| | 注释 | 注解 |
|---|---|---|
| 写法 | `// ...`、`/* ... */`、`/** ... */` | `@Entity`、`@Override` |
| 给谁看 | **人** | **编译器 / 框架 / 反射** |
| 编译后 | 丢弃 | 可能保留进 `.class` 文件 |
| 写错会怎样 | 无所谓 | 编译错误，或运行时报错 |

```java
// 这是注释：解释 passwordHash 为什么不是明文
@Column(name = "password_hash")   // 这是注解：告诉 JPA 映射到哪一列
private String passwordHash;
```

一句话：**注解是"给工具看的注释"**。

---

## 1. 注解到底是什么

注解本身**不执行任何逻辑**，它只是往 class 文件里塞一段结构化元数据。真正干活的是"读注解的人"。

```
你写下 @Entity
      │
      ├─→ 编译器（javac）：只检查语法，不做事          ← @Override 属于这类
      │
      ├─→ 框架启动时扫描（Spring / Hibernate）：读到 @Entity，
      │      就知道"这个类要映射表"、"启动时校验列"      ← @Entity 属于这类
      │
      └─→ 运行时反射（JUnit / Spring Test）：            ← @Test 属于这类
             读到 @Test 就把这个方法当测试跑
```

这就是为什么 `@Override` 删掉程序照样跑（它只帮编译器检查），而 `@Entity` 删掉就会出问题（没人给这个类建映射了）。

### 关键概念：Retention（保留策略）

每个注解都在定义时声明了"活到哪一步"：

| 策略 | 编译后是否保留 | 例子 |
|---|---|---|
| `SOURCE` | 丢弃 | `@Override`、`@SuppressWarnings` |
| `CLASS` | 保留在 class 但不反射可见 | 少数框架 |
| `RUNTIME` | 保留且反射可读 | `@Entity`、`@Test`、`@Bean` |

框架能靠注解工作，前提都是 `RUNTIME`。

### 关键概念：元注解（注解上的注解）

看 `@SpringBootApplication` 的真实定义：

```java
@SpringBootConfiguration     // 说明它是个配置类
@EnableAutoConfiguration     // 触发自动配置
@ComponentScan               // 扫描当前包及子包的 @Component
public @interface SpringBootApplication { }
```

所以那**一行** `@SpringBootApplication` 等价于三行。JavaWeb 里你要在 `web.xml` 写 servlet 映射、在 `applicationContext.xml` 写 `<bean>`，现在被这一行替代——这就是"约定优于配置"。

---

## 2. 本项目注解总表

按"谁在读"分类，比按字母排序有用。

### 2.1 Spring 容器（谁的 new 交给谁）

| 注解 | 用在哪 | 作用 | 不写会怎样 |
|---|---|---|---|
| `@SpringBootApplication` | `ApiApplication` | 启动类：开自动配置 + 扫包 | 应用起不来 |
| `@Configuration` | `PasswordConfig` | 声明"这是一个配置类" | `@Bean` 不被处理 |
| `@Bean` | `passwordEncoder()` 方法 | 把这个方法的返回值放进容器 | 注入 `PasswordEncoder` 时找不到 bean |
| `@Service` / `@Component` | 之后写 Service 时 | 把类交给容器管理（`new` 由容器做） | 注入时报 `NoSuchBeanDefinitionException` |
| `@Autowired` | **仅测试类** | 让容器把字段填上 | 字段是 null |
| `@RequiredArgsConstructor` | Service/Controller | Lombok 生成"所有 final 字段"的构造器 | 手写构造器 |

> 规范 `J-06`：**业务代码禁止 `@Autowired` 字段注入**，只能用构造器注入。测试类可以用，因为测试不是被别人依赖的对象。

### 2.2 Web 层（HTTP 怎么进来）

| 注解 | 作用 | JavaWeb 里的对应物 |
|---|---|---|
| `@RestController` | 标记"这是个 HTTP 处理方法集合"，返回值直接转 JSON | `@WebServlet` + 手写 `resp.getWriter().write(json)` |
| `@RequestMapping("/api/v1/health")` | 类级别的路径前缀 | `web.xml` 里的 `<url-pattern>` |
| `@GetMapping` | 只接 GET | `doGet()` |
| `@PostMapping` / `@PutMapping` / `@DeleteMapping` | 只接对应方法 | `doPost()` 等 |
| `@PathVariable` | URL 里的 `{id}` → 方法参数 | `req.getParameter()`（但语义不同：路径段 vs 查询串） |
| `@RequestParam` | `?page=2` → 方法参数 | `req.getParameter("page")` |
| `@RequestBody` | 请求体 JSON → 对象 | 手写 `req.getReader()` + 解析 |
| `@Valid` | 触发参数校验（`@NotBlank` 等） | 手写一堆 `if (name == null)` |

### 2.3 持久化层（Java 对象怎么变表）

| 注解 | 作用 | 不写会怎样 |
|---|---|---|
| `@Entity` | 这个类是实体，要映射表 | 不参与映射，`save()` 报 `Not an entity` |
| `@Table(name = "users")` | 指定表名 | 默认类名转表名 `user` → PG 保留字，SQL 语法错误 |
| `@Id` | 主键字段 | 启动报 `No identifier specified for entity` |
| `@Column(...)` | 列名 / 长度 / 是否可空 / 是否可更新 | 按字段名推断（驼峰转下划线） |
| `@Enumerated(EnumType.STRING)` | 枚举存字符串 | 默认存序号 0/1，枚举顺序一变语义全错 |
| `@CreationTimestamp` | Hibernate 在 INSERT 时填时间 | 字段为 null，或依赖数据库默认值 |
| `@Query("...")` | 手写 JPQL | 只能用方法名派生查询，表达不了 `lower()` 等 |

### 2.4 测试（怎么让测试框架认出你）

| 注解 | 作用 |
|---|---|
| `@Test` | 标记一个测试方法（JUnit 5） |
| `@SpringBootTest` | 启动完整 Spring 容器（含数据库、Flyway） |
| `@ActiveProfiles("test")` | 激活 `test` profile → 加载 `application-test.yml` |
| `@Transactional`（测试上） | 每个测试方法结束后**回滚**，不污染数据库 |
| `@Disabled("原因")` | 跳过这个测试（骨架阶段） |
| `@ParameterizedTest` + `@MethodSource` | 用一个方法生成多组参数，跑同一个测试 |

### 2.5 Lombok（写的时候省代码，编译后真生成方法）

| 注解 | 生成什么 | 本项目规则 |
|---|---|---|
| `@Getter` | 每个字段的 getter（boolean 生成 `isXxx()`） | 实体只用这个 |
| `@Setter` | setter | **实体禁止**（能绕过 token_version 自增） |
| `@NoArgsConstructor(access = PROTECTED)` | 无参构造器，且是 protected | JPA 需要，业务不许用 |
| `@RequiredArgsConstructor` | 所有 `final` 字段的构造器 | Service/Controller 的依赖注入 |
| `@Data` | getter + setter + equals + hashCode + toString | **实体禁止**（`J-22`：equals 会踩代理） |
| `@Builder` | 链式构造 | **实体禁止**（`J-23`） |

---

## 3. 六个最容易搞混的点

### 3.1 `@Configuration` 和 `@Component` 有什么区别

`@Configuration` 是特殊的 `@Component`，额外做了一件事：**用 CGLIB 代理这个类**，让 `@Bean` 方法被调用时返回容器里的同一个实例（而不是每次 new 一个）。

```java
@Configuration
class PasswordConfig {
    @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(12); }
}
```

没有这个代理的话，你调用两次 `encoder()` 会得到两个不同对象——单例就破了。

### 3.2 `@Entity` 加 `@Table` 为什么还要写列名

Hibernate 有默认命名策略（`passwordHash` → `password_hash`），大多数情况能对上。但规范 `J-28` 要求表名显式写死，列名在有歧义时显式写（如 `email_public`、`total_points`）。

**默认策略是"猜"，显式声明是"承诺"**。字段改名时，猜的结果会静默变化，而显式声明不会。

### 3.3 `@Transactional` 为什么有时"不起作用"

它是 **AOP 代理**实现的：你调 `userService.changePassword()` 时，实际调用的是代理对象，代理开事务 → 调真方法 → 提交/回滚。

所以**同一个类内部自调用会失效**：

```java
public void a() { this.b(); }          // 走的是 this，不是代理 → b 上的事务不生效
@Transactional public void b() { ... }
```

这是初学者最容易踩的坑之一。解决办法是把 `b()` 挪到另一个 bean，或者用 `AopContext.currentProxy()`（不推荐）。

### 3.4 `@SpringBootTest` 和普通 JUnit 的区别

| | 普通 `@Test` | `@SpringBootTest` |
|---|---|---|
| 启动容器 | 否 | 是（Spring + 数据源 + Flyway + MVC） |
| 速度 | 毫秒 | 秒级（本项目约 8~12 秒） |
| 能测什么 | 纯对象逻辑 | 依赖注入、SQL、HTTP 端点 |

所以 `UserTest`（纯对象）不用容器，`UserRepositoryTest`（要真库）才用。启动容器一次要十几秒，全放一起会让人不想跑测试。

### 3.5 `record` 和普通 class 的区别

```java
public record ApiResponse<T>(boolean ok, T data, ErrorBody error) { }
```

一行等价于：

- 所有字段 `private final`
- 全参构造器
- 访问器 `ok()`、`data()`、`error()`（**不是** `getOk()`）
- 自动 `equals` / `hashCode` / `toString`
- 类本身是 `final`，不能继承

它**没有 setter**——天生不可变。适合 DTO、返回值、坐标类；不适合实体（JPA 需要可变 + 无参构造器）。

`record` 里可以加方法（比如 `static ApiResponse<T> ok(T data)`）和静态工厂，这正是本项目 DTO 转换的写法。

### 3.6 `Optional` 到底在防什么

```java
Optional<User> findByUsername(String u);
```

如果返回 `User`，"查不到"只能用 `null`。`null` 会一路传播：

```java
User u = repo.findByUsername("nobody");   // null
u.getRole();                               // NPE，堆栈在很远的地方
```

`Optional` 把"可能没有"写进**类型**：调用方想拿值，就得先处理空的分支。

```java
// 不推荐：get() 等于把 Optional 又变回 null
User u = optional.get();

// 推荐
User u = optional.orElseThrow(() -> new BusinessException(NOT_FOUND));
```

常用方法：`isPresent()` / `isEmpty()` / `map()` / `orElse()` / `orElseThrow()`。

规范 `J-30`：Optional 只用于"可能查不到"的返回值，**不要当字段类型、不要当方法参数**。

---

## 4. Java 语法点（本项目实际出现的）

### 4.1 泛型：`<T>` 是什么意思

```java
public record ApiResponse<T>(boolean ok, T data, ErrorBody error) { }
```

`T` 是"类型占位符"。`ApiResponse<Map<String,Object>>` 表示 `data` 是 `Map`；`ApiResponse<User>` 表示 `data` 是 `User`。编译期就能检查类型，不用强转。

`JpaRepository<User, UUID>` 的两个参数分别是"实体类型"和"主键类型"。所以 `findById(UUID)` 的参数类型是确定的——写错类型编译不过。

### 4.2 `var`：只是省了类型名

```java
var current = flyway.info().current();   // 编译器推断出类型，运行时毫无区别
```

**不是动态类型**。Java 仍然是强类型语言，`var` 只在编译期帮你省字。它只能用于局部变量，不能用于字段、方法参数、返回值。

### 4.3 位运算（`UuidUtils` 里那些）

这四个符号让很多人劝退，其实规则很简单：

| 符号 | 名称 | 含义 | 例子（8 位示意） |
|---|---|---|---|
| `&` | 按位与 | 两个都是 1 才为 1 | `1010 & 1100 = 1000` |
| `\|` | 按位或 | 有一个是 1 就是 1 | `1010 \| 1100 = 1110` |
| `<<` | 左移 | 右边补 0，等于 ×2ⁿ | `0001 << 3 = 1000` |
| `>>>` | 无符号右移 | 左边补 0，等于 ÷2ⁿ | `1000 >>> 3 = 0001` |

**掩码（mask）**是 `&` 最常见的用途：只保留某些位。

```java
long unixTsMs = System.currentTimeMillis() & 0xFFFF_FFFF_FFFFL;  // 只留低 48 位
```

`0xFFFF_FFFF_FFFF` 是 48 个 1，与它做 `&`，就只留下低 48 位（时间戳用 48 位存，够用到公元 10889 年）。下划线 `_` 只是给人看的分隔符，编译时忽略；结尾的 `L` 表示这是 `long` 字面量（不加会被当 `int`，48 位放不下）。

再看这一句：

```java
long msb = (unixTsMs << 16) | 0x7000L | (hi >>> 52 & 0xFFFL);
```

拆开读：

- `unixTsMs << 16`：时间戳整体左移 16 位，右边空出 16 位
- `| 0x7000L`：把"版本号"那 4 位写成 `0111`（=7），其余位不变
- `| (hi >>> 52 & 0xFFF)`：取 `hi` 的高 12 位，填进刚空出的低 12 位

**用 `|` 而不是 `+`** 的原因：`+` 可能进位污染相邻位，`|` 只置位，在已知目标位是 0 时更安全、意图更明确。

### 4.4 枚举

```java
public enum Role { ADMIN, MEMBER }
```

枚举不是"常量集合"那么简单：它是**类型**，编译期就能挡住 `Role role = "ADMIN"` 这种错误。配合 `@Enumerated(STRING)` 存名字，顺序变了也不会错。

### 4.5 时间类型：三个别再混

| 类型 | 含义 | 用在哪 |
|---|---|---|
| `Instant` | UTC 时间戳（机器用） | 日志、内部计算 |
| `LocalDate` | 只有日期，没有时间与时区 | 打卡日期（业务日期） |
| `OffsetDateTime` | 日期 + 时间 + **时区偏移** | 数据库 `timestamptz` 列 |

**为什么不用 `LocalDateTime`**：它没有时区信息。中国服务器写进去 14:00，UTC 服务器读出来还是 14:00，但那是完全不同的两个时刻。打卡日期差一天、token 过期时间算错，都是这个引起的。规范 `J-14` / `J-38` 禁用它。

### 4.6 Lambda 与方法引用

```java
.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND))
```

`() -> ...` 是"匿名函数"，把一个行为当参数传。你 JavaWeb 里写 `Comparator` 匿名内部类的地方，现在都可以这么写：

```java
// 以前
new Comparator<User>() {
    public int compare(User a, User b) { return a.getUsername().compareTo(b.getUsername()); }
}
// 现在
Comparator.comparing(User::getUsername)     // User::getUsername 是方法引用
```

本项目用在哪：`orElseThrow(() -> ...)`、`assertThatThrownBy(() -> ...)`、`Stream.of(...)` 的参数构造、`Optional.map(...)`。

### 4.7 Stream（测试里用到一点）

```java
static Stream<Case> matrix() {
    return Stream.of(
        new Case("GET", "/api/v1/health", null, 200),
        new Case("GET", "/api/v1/me", Role.MEMBER, 200)
    );
}
```

`Stream` 是"流水线"：`stream()` 开流 → 中间操作（`filter/map/sorted`）→ 终端操作（`toList/forEach/count`）才真正执行。中间操作是**惰性**的，不写终端操作什么都不会发生。

本项目目前用得很少，等写统计接口时会多起来。

### 4.8 `static` 工厂与私有构造器

```java
public final class UuidUtils {
    private UuidUtils() { }
    public static UUID v7() { ... }
}
```

- `final` 类：不能被继承（工具类没理由被继承）
- `private` 构造器：外部不能 `new`，把"误用"变成编译错误

同样的手法也用在实体上（`private` → `protected`，因为 JPA 要用）。

---

## 5. 注释该怎么写（规范 `G-06` / `J-39`）

### 5.1 三种注释

```java
// 单行注释：解释这一行为什么这么写
/* 块注释：临时屏蔽代码（不要用它写文档） */
/**
 * Javadoc：类/方法的契约说明，IDE 能提示，可被 javadoc 生成文档
 * <p>段落用 <p> 分隔，代码用 {@code ...}，链接用 {@link Xxx}
 */
```

### 5.2 本项目的要求

**写"为什么"，不写"做什么"**：

```java
// 差：调用 userRepository.save
userRepository.save(user);

// 好：先 flush 才能让 @CreationTimestamp 生效，
//     同一事务内 findById 只会命中一级缓存
userRepository.saveAndFlush(user);
```

**每个 public 类一句 Javadoc，说明它为什么存在**（`J-39`）：

```java
/**
 * 密码哈希的唯一入口。
 * <p>为什么单独一个配置类：SecurityConfig 管"谁能访问哪个路径"，
 * 这里管"密码怎么存"，两件事的变更频率不同。
 */
```

**不写废话**：getter/setter、`setName` 上方写"// 设置 name"属于噪声，规范明确禁止。

### 5.3 需要写注释的四个地方

1. **反直觉的代码**：为什么要 flush、为什么要 `Locale.ROOT`
2. **业务规则的来源**：`tokenVersion++` 是因为"改密码要踢人下线（S-02）"
3. **权衡的结果**：为什么这里查库而不缓存
4. **TODO**：必须带 `TODO(负责人, 日期): 原因`（`G-03`）

---

## 6. 一页纸速查

```
注解给工具看，注释给人看。

容器：@Configuration 配 @Bean  |  @Service/@Component 交给自己管  |  构造器注入优先
Web ：@RestController + @GetMapping/@PostMapping  |  @PathVariable / @RequestParam / @RequestBody
JPA ：@Entity + @Table + @Id + @Column  |  @Enumerated(STRING)  |  @Query(JPQL)
测试：@Test  |  @SpringBootTest(+@ActiveProfiles)  |  @Transactional 回滚  |  @Disabled
Lombok：@Getter 可  |  @Setter 实体禁  |  @Data/@Builder 实体禁  |  @RequiredArgsConstructor 用于注入

record     ：不可变 DTO，访问器是 name() 而不是 getName()
Optional   ：只用于可能查不到的返回值，别当字段/参数
var        ：局部变量的类型省略，仍是强类型
& | << >>> ：掩码 / 置位 / 移位，配 _ 和 L 字面量
时间       ：Instant(机器) / LocalDate(业务日期) / OffsetDateTime(带时区)
```

---

## 7. 练习：读这段代码，说出每个符号的作用

```java
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static User create(String username, String email, String hash, Role role) {
        User user = new User();
        user.id = UuidUtils.v7();
        user.username = username.trim().toLowerCase(Locale.ROOT);
        user.role = Objects.requireNonNull(role, "role 不能为空");
        return user;
    }
}
```

<details>
<summary>参考答案</summary>

- `@Entity`：类参与 JPA 映射；没有它 `save()` 直接报错。
- `@Table(name="users")`：表名写死为 `users`，避免默认推导成 `user`（PG 保留字）。
- `@Getter`：Lombok 编译期生成 `getId()`、`getRole()`、`getCreatedAt()`（`boolean` 字段会生成 `isXxx()`）。
- `@NoArgsConstructor(access=PROTECTED)`：生成 protected 无参构造器，给 Hibernate 反射用，业务代码无法 `new User()`。
- `@Id`：声明主键字段；缺了启动失败。
- `UUID`：128 位标识，这里由 `UuidUtils.v7()` 在应用侧生成。
- `@Enumerated(EnumType.STRING)`：枚举按名字存 `'ADMIN'`，不是序号。
- `@Column(nullable=false, length=10)`：NOT NULL + varchar(10)，与迁移里的定义一致；不放宽会被 `validate` 拦住。
- `@CreationTimestamp`：Hibernate 在 INSERT 时填时间；注意只在 flush 时生效。
- `updatable = false`：生成 UPDATE 语句时跳过此列（创建时间不该被改）。
- `OffsetDateTime`：带时区偏移的时间，对应 `timestamptz`。
- `static` 工厂 + `new User()`：`create` 内部可以使用 protected 构造器（同类内部可见），外部不行。
- `toLowerCase(Locale.ROOT)`：与地区无关的小写化，避免土耳其语 `I→ı` 的问题。
- `Objects.requireNonNull(role, msg)`：null 立即抛 NPE 并带上消息，比等到数据库报错更早暴露。
- `UuidUtils.v7()`：静态方法调用，类用 private 构造器保证不被实例化。

</details>

---

## 8. 延伸阅读

- Java 语言规范（注解）：<https://docs.oracle.com/javase/specs/jls/se21/html/jls-9.html#jls-9.6>
- Spring 核心容器（IoC）：<https://docs.spring.io/spring-framework/reference/core/beans.html>
- Spring Data JPA 查询方法：<https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html>
- Lombok 官方文档：<https://projectlombok.org/features/>
- Java 17 `record`：<https://docs.oracle.com/en/java/javase/17/language/records.html>
