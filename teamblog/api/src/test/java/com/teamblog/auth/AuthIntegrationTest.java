package com.teamblog.auth;

import com.jayway.jsonpath.JsonPath;
import com.teamblog.user.Role;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录 → 刷新 → 登出的完整链路（1.3 验收：同一 rt 用两次，第二次触发全量撤销）。
 *
 * <p>为什么这个类**不加** @Transactional：重放检测会调用 REQUIRES_NEW 的
 * {@link SessionRevoker}，那是一个独立事务，测试事务回滚管不到它。包在 @Transactional
 * 里会得到"数据留下了、断言却以为回滚了"的假象。这里改为显式清理（@AfterEach）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    private static final String RAW_PASSWORD = "S3cret!pass";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<String> createdUsernames = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // 删用户会级联删掉它的 refresh_tokens（V1 里的 ON DELETE CASCADE）
        createdUsernames.forEach(name ->
                userRepository.findByUsername(name).ifPresent(userRepository::delete));
        createdUsernames.clear();
    }

    @Test
    void 登录成功_下发access与rt的httpOnly_Cookie_并能访问受保护端点() throws Exception {
        String username = createUser();

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                // refresh 只能在 Cookie 里，绝不能出现在响应体（A-24）
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();

        String rawRefresh = refreshCookieOf(login);
        assertThat(rawRefresh).isNotBlank();
        assertThat(login.getResponse().getHeader("Set-Cookie")).contains("HttpOnly");

        String accessToken = JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));
    }

    @Test
    void 密码错误与用户不存在_返回相同的401文案() throws Exception {
        String username = createUser();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("用户名或密码错误"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("nobody_here", RAW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("用户名或密码错误"));
    }

    @Test
    void 刷新_轮换Cookie_新access可用_旧的access仍按自身过期时间() throws Exception {
        String username = createUser();
        String rawRefresh = loginAndGetRefreshCookie(username);

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("rt", rawRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        String rotated = refreshCookieOf(refreshed);
        assertThat(rotated).isNotBlank().isNotEqualTo(rawRefresh);

        String newAccess = JsonPath.read(refreshed.getResponse().getContentAsString(), "$.data.accessToken");
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk());
    }

    @Test
    void 同一rt用两次_第二次SESSION_REVOKED_且已签发的access立刻失效() throws Exception {
        String username = createUser();
        String firstRefresh = loginAndGetRefreshCookie(username);

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("rt", firstRefresh)))
                .andExpect(status().isOk())
                .andReturn();
        String accessFromRefresh = JsonPath.read(
                refreshed.getResponse().getContentAsString(), "$.data.accessToken");

        // 重放：再用一次已经被轮换掉的旧票
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("rt", firstRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_REVOKED"));

        // 全量撤销：连刚才换来的 access 也必须失效（token_version 被 +1）
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessFromRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_REVOKED"));

        // 新票同样被撤销：攻击者拿不到续期的机会。
        // 注意这里返回的是 UNAUTHENTICATED 而不是 SESSION_REVOKED —— 全量撤销写的是
        // "无替换关系的作废"，和登出产生的作废在库里长得一样；此时危害已经被止住，
        // 不需要再区分（见 RefreshTokenService.rotate 里的两类作废）。
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("rt", refreshCookieOf(refreshed))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 登出_清掉Cookie_再刷新被拒绝且不触发全量撤销() throws Exception {
        String username = createUser();
        String rawRefresh = loginAndGetRefreshCookie(username);

        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("rt", rawRefresh)))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Set-Cookie"))
                        .contains("Max-Age=0"));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("rt", rawRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 连续五次失败后_第六次即使密码正确也返回429并带RetryAfter() throws Exception {
        String username = createUser();
        String ip = uniqueIp();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson(username, "wrong-password")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        }

        // 第 6 次用**正确密码**也必须被拦：限流在验密码之前（1.4 验收）
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, RAW_PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
                // 文案与密码错误一致：不额外透露"这个账号被锁了"
                .andExpect(jsonPath("$.error.message").value("用户名或密码错误"));
    }

    @Test
    void 登录成功会清零失败计数() throws Exception {
        String username = createUser();
        String ip = uniqueIp();

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson(username, "wrong-password")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, RAW_PASSWORD)))
                .andExpect(status().isOk());

        // 成功后再错 4 次，仍应是 401 而不是 429（计数已清零）
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson(username, "wrong-password")))
                    .andExpect(status().isUnauthorized());
        }
    }

    private String createUser() {
        String username = "it" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        userRepository.saveAndFlush(User.create(
                username, username + "@example.com", passwordEncoder.encode(RAW_PASSWORD), Role.MEMBER));
        createdUsernames.add(username);
        return username;
    }

    private String loginAndGetRefreshCookie(String username) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return refreshCookieOf(login);
    }

    private static String loginJson(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    /**
     * 每个用例用一个独立 IP。
     *
     * <p>限流按 IP 计数，如果所有用例都用 MockMvc 的默认来源 127.0.0.1，
     * 一个用例攒够 5 次失败就会把后面的用例一起拦掉——测试会变成"看执行顺序"，
     * 违反 Q-09。用 X-Forwarded-For 隔离，也顺带验证了生产环境经 Nginx 的场景。
     */
    private static String uniqueIp() {
        return "198.51.100." + (1 + java.util.concurrent.ThreadLocalRandom.current().nextInt(254));
    }

    /** 从 Set-Cookie 头里抠出 rt 的值（ResponseCookie 走 header，不走 response.addCookie）。 */
    private static String refreshCookieOf(MvcResult result) {
        String header = result.getResponse().getHeader("Set-Cookie");
        assertThat(header).as("响应必须下发 refresh Cookie").isNotNull();
        int start = header.indexOf("rt=") + 3;
        int end = header.indexOf(';', start);
        return header.substring(start, end < 0 ? header.length() : end);
    }
}
