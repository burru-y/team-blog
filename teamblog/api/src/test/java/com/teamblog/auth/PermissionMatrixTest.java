package com.teamblog.auth;

import com.teamblog.admin.AdminInviteService;
import com.teamblog.user.Role;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 权限矩阵（1.6）：三种身份 × 全部接口。
 *
 * <p>两个测试缺一不可：
 * <ul>
 *   <li>参数化用例 —— 验证"现在是对的"</li>
 *   <li>{@link #每个已注册接口都必须出现在矩阵里} —— 验证"以后不会漏"</li>
 * </ul>
 * 后者才是这套东西的价值：新人加了接口却忘了配权限，CI 直接红，而不是上线后被扫出来（Q-05）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PermissionMatrixTest {

    private static final String RAW_PASSWORD = "S3cret!pass";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminInviteService inviteService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private String adminToken;
    private String memberToken;
    private String inviteTokenValue;

    @BeforeEach
    void setUp() {
        User admin = persistUser("matrix_admin", Role.ADMIN);
        User member = persistUser("matrix_member", Role.MEMBER);
        adminToken = jwtService.issueAccessToken(admin);
        memberToken = jwtService.issueAccessToken(member);
        inviteTokenValue = inviteService.create(admin.getId(), "invited@example.com", Role.MEMBER).rawToken();
    }

    /** 用例表：接口 × 身份 → 期望状态码。新增接口必须先在这里加一行。 */
    static Stream<Case> matrix() {
        return Stream.of(
                // 公开：健康检查
                new Case(HttpMethod.GET, "/api/v1/health", null, 200),

                // 公开：登录 —— 空 body 得到 400，说明请求到了 Controller 而不是被安全链拦下
                new Case(HttpMethod.POST, "/api/v1/auth/login", null, 400),

                // 公开：刷新 —— 没带 Cookie 时由业务层拒绝
                new Case(HttpMethod.POST, "/api/v1/auth/refresh", null, 401),

                // 公开：登出 —— 幂等，没有 Cookie 也返回 200
                new Case(HttpMethod.POST, "/api/v1/auth/logout", null, 200),

                // 公开：邀请信息与兑换
                new Case(HttpMethod.GET, "/api/v1/invites/{token}", null, 200),
                new Case(HttpMethod.POST, "/api/v1/invites/{token}/accept", null, 400),

                // 公开：团队公开资料（批次 2）
                new Case(HttpMethod.GET, "/api/v1/users", null, 200),
                new Case(HttpMethod.GET, "/api/v1/users/{username}", null, 200),

                // 公开：成果流与详情（详情用一个不存在的 id，期望 404 —— 证明没被安全链拦）
                new Case(HttpMethod.GET, "/api/v1/projects", null, 200),
                new Case(HttpMethod.GET, "/api/v1/projects/{id}", null, 404),

                // 公开：学习内容（列表必须带 username，缺参数返回 400）
                new Case(HttpMethod.GET, "/api/v1/learning-logs", null, 400),
                new Case(HttpMethod.GET, "/api/v1/learning-logs/{id}", null, 404),

                // 需要登录：发布 / 编辑学习内容
                new Case(HttpMethod.POST, "/api/v1/learning-logs", null, 401),
                new Case(HttpMethod.POST, "/api/v1/learning-logs", Role.MEMBER, 400),
                new Case(HttpMethod.PUT, "/api/v1/learning-logs/{id}", null, 401),

                // 公开：留言（空 body 400，证明没被安全链拦；蜜罐/限流在 Service）
                new Case(HttpMethod.POST, "/api/v1/feedback", null, 400),

                // 管理员专属：成员与成果管理
                new Case(HttpMethod.GET, "/api/v1/admin/users", null, 401),
                new Case(HttpMethod.GET, "/api/v1/admin/users", Role.MEMBER, 403),
                new Case(HttpMethod.GET, "/api/v1/admin/users", Role.ADMIN, 200),
                new Case(HttpMethod.PATCH, "/api/v1/admin/users/{id}/role", null, 401),
                new Case(HttpMethod.GET, "/api/v1/admin/projects", null, 401),
                new Case(HttpMethod.GET, "/api/v1/admin/projects", Role.MEMBER, 403),
                new Case(HttpMethod.GET, "/api/v1/admin/projects", Role.ADMIN, 200),

                // 管理员专属：留言箱
                new Case(HttpMethod.GET, "/api/v1/admin/feedback", null, 401),
                new Case(HttpMethod.GET, "/api/v1/admin/feedback", Role.MEMBER, 403),
                new Case(HttpMethod.GET, "/api/v1/admin/feedback", Role.ADMIN, 200),
                new Case(HttpMethod.GET, "/api/v1/admin/feedback/count", null, 401),
                new Case(HttpMethod.GET, "/api/v1/admin/feedback/count", Role.MEMBER, 403),
                new Case(HttpMethod.GET, "/api/v1/admin/feedback/count", Role.ADMIN, 200),
                new Case(HttpMethod.POST, "/api/v1/admin/feedback/{id}/read", null, 401),

                // 公开：评论列表（缺参数 400，证明没被安全链拦）
                new Case(HttpMethod.GET, "/api/v1/comments", null, 400),

                // 评论：发表与删除需要登录
                new Case(HttpMethod.POST, "/api/v1/comments", null, 401),
                new Case(HttpMethod.POST, "/api/v1/comments", Role.MEMBER, 400),
                new Case(HttpMethod.DELETE, "/api/v1/comments/{id}", null, 401),

                // 公开：团队热力图
                new Case(HttpMethod.GET, "/api/v1/stats/heatmap", null, 200),

                // 打卡：需要登录（矩阵里只打一次，避免同一事务内重复 409）
                new Case(HttpMethod.POST, "/api/v1/checkins", null, 401),
                new Case(HttpMethod.POST, "/api/v1/checkins", Role.MEMBER, 201),
                new Case(HttpMethod.GET, "/api/v1/checkins/status", null, 401),
                new Case(HttpMethod.GET, "/api/v1/checkins/status", Role.MEMBER, 200),

                // 需要登录：上传（图片落盘 / 代码转 Markdown）
                new Case(HttpMethod.POST, "/api/v1/uploads/images", null, 401),
                new Case(HttpMethod.POST, "/api/v1/uploads/code", null, 401),

                // 需要登录：发布 / 编辑 / 下架（归属校验在 Service，见 ProjectService）
                new Case(HttpMethod.POST, "/api/v1/projects", null, 401),
                new Case(HttpMethod.POST, "/api/v1/projects", Role.MEMBER, 400),
                new Case(HttpMethod.PUT, "/api/v1/projects/{id}", null, 401),
                new Case(HttpMethod.DELETE, "/api/v1/projects/{id}", null, 401),

                // 需要登录
                new Case(HttpMethod.GET, "/api/v1/me", null, 401),
                new Case(HttpMethod.GET, "/api/v1/me", Role.MEMBER, 200),
                new Case(HttpMethod.GET, "/api/v1/me", Role.ADMIN, 200),

                // 管理员专属：手动触发积分对账
                new Case(HttpMethod.POST, "/api/v1/admin/points/reconcile", null, 401),
                new Case(HttpMethod.POST, "/api/v1/admin/points/reconcile", Role.MEMBER, 403),
                new Case(HttpMethod.POST, "/api/v1/admin/points/reconcile", Role.ADMIN, 200),

                // 管理员专属
                new Case(HttpMethod.POST, "/api/v1/admin/invites", null, 401),
                new Case(HttpMethod.POST, "/api/v1/admin/invites", Role.MEMBER, 403),
                new Case(HttpMethod.POST, "/api/v1/admin/invites", Role.ADMIN, 200)
        );
    }

    @ParameterizedTest(name = "[{index}] {0} {1} 作为 {2} => {3}")
    @MethodSource("matrix")
    void 权限矩阵_三身份访问每个端点_状态码符合预期(Case testCase) throws Exception {
        String path = testCase.pathPattern()
                .replace("{token}", inviteTokenValue)
                .replace("{username}", "matrix_member")
                .replace("{id}", "00000000-0000-7000-8000-000000000000");

        MockHttpServletRequestBuilder builder = request(testCase.method(), path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyFor(testCase.pathPattern()));

        String token = testCase.role() == null ? null
                : testCase.role() == Role.ADMIN ? adminToken : memberToken;
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        int actual = mockMvc.perform(builder).andReturn().getResponse().getStatus();

        assertThat(actual)
                .as("%s %s 作为 %s", testCase.method(), path,
                        testCase.role() == null ? "匿名" : testCase.role())
                .isEqualTo(testCase.expectedStatus());
    }

    @Test
    void 匿名访问受保护端点_返回UNAUTHENTICATED() throws Exception {
        mockMvc.perform(request(HttpMethod.GET, "/api/v1/me"))
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 成员访问管理员端点_返回FORBIDDEN() throws Exception {
        mockMvc.perform(request(HttpMethod.POST, "/api/v1/admin/invites")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}"))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    /** 扫描全部已注册的 /api 端点，要求每个都出现在矩阵里（把 Q-05 变成 CI 检查）。 */
    @Test
    void 每个已注册接口都必须出现在矩阵里() {
        Set<String> covered = new TreeSet<>();
        matrix().forEach(c -> covered.add(c.method() + " " + c.pathPattern()));

        Set<String> registered = new TreeSet<>();
        for (Map.Entry<RequestMappingInfo, ?> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/api/")) {
                    continue;
                }
                Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                if (methods.isEmpty()) {
                    registered.add("ANY " + pattern);
                } else {
                    methods.forEach(m -> registered.add(m.name() + " " + pattern));
                }
            }
        }

        assertThat(registered).isNotEmpty();
        assertThat(covered)
                .as("以下接口没有权限用例，请到 PermissionMatrixTest.matrix() 补一行：%s",
                        registered.stream().filter(r -> !covered.contains(r)).toList())
                .containsAll(registered);
    }

    /**
     * 矩阵的两个完整性约束：
     * <ol>
     *   <li>每个接口都要有**匿名**用例——逼你明确"未登录时它给什么"（200 还是 401）</li>
     *   <li>只要有用例用了 ADMIN 身份，就必须同时有 MEMBER 用例——证明角色分界真的被验证过，
     *       而不是"管理员能进"就完事</li>
     * </ol>
     *
     * <p>第二条是 Q-05 的机械化：删掉『成员打管理员接口 → 403』那行，这里立刻红。
     */
    @Test
    void 矩阵完整性_每个接口都有匿名用例_且管理员用例必配成员用例() {
        Map<String, List<Case>> byEndpoint = matrix().collect(
                java.util.stream.Collectors.groupingBy(c -> c.method() + " " + c.pathPattern()));

        byEndpoint.forEach((endpoint, cases) -> {
            assertThat(cases.stream().anyMatch(c -> c.role() == null))
                    .as("%s 缺少匿名用例：未登录时它给什么？", endpoint)
                    .isTrue();

            boolean hasAdmin = cases.stream().anyMatch(c -> c.role() == Role.ADMIN);
            boolean hasMember = cases.stream().anyMatch(c -> c.role() == Role.MEMBER);
            assertThat(!hasAdmin || hasMember)
                    .as("%s 有管理员用例，但缺少成员用例（Q-05：必须验证低权限角色被拒）", endpoint)
                    .isTrue();
        });
    }

    private static String bodyFor(String pathPattern) {
        if (pathPattern.endsWith("/accept") || pathPattern.endsWith("/auth/login")) {
            // 空 body：期望 400 校验失败，证明请求到达了 Controller
            return "{}";
        }
        if (pathPattern.endsWith("/admin/invites")) {
            return "{\"role\":\"MEMBER\"}";
        }
        return "";
    }

    private User persistUser(String username, Role role) {
        return userRepository.saveAndFlush(User.create(
                username, username + "@example.com", passwordEncoder.encode(RAW_PASSWORD), role));
    }

    record Case(HttpMethod method, String pathPattern, Role role, int expectedStatus) {
        @Override
        public String toString() {
            return role == null ? "匿名" : role.name();
        }
    }
}
