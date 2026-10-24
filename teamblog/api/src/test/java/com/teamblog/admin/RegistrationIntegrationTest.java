package com.teamblog.admin;

import com.jayway.jsonpath.JsonPath;
import com.teamblog.auth.JwtService;
import com.teamblog.user.Role;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 邀请注册的完整链路（1.5 验收：同一邀请用两次，第二次 410）。
 *
 * <p>不加 @Transactional：邀请的"占用"用的是条件更新，要验证真实落库后的行为；
 * 而且删除用户时 FK（created_by）会挡住，必须显式先删邀请（见 cleanUp）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationIntegrationTest {

    private static final String RAW_PASSWORD = "S3cret!pass";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminInviteRepository inviteRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<String> createdUsernames = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // admin_invites.created_by 没有 ON DELETE CASCADE，必须先把邀请删掉
        inviteRepository.deleteAll();
        createdUsernames.forEach(name ->
                userRepository.findByUsername(name).ifPresent(userRepository::delete));
        createdUsernames.clear();
    }

    @Test
    void 管理员生成邀请_受邀者注册成功并能登录() throws Exception {
        User admin = createUser("admin", Role.ADMIN);
        String token = createInvite(admin, "newbie@example.com", "MEMBER");

        String username = uniqueUsername("newbie");
        createdUsernames.add(username);
        mockMvc.perform(post("/api/v1/invites/" + token + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, RAW_PASSWORD, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.email").value("newbie@example.com"))
                .andExpect(jsonPath("$.data.role").value("MEMBER"))
                // 响应里绝不能出现密码哈希
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + RAW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void 同一邀请用两次_第二次410() throws Exception {
        User admin = createUser("admin", Role.ADMIN);
        String token = createInvite(admin, null, "MEMBER");

        String first = uniqueUsername("first");
        createdUsernames.add(first);
        mockMvc.perform(post("/api/v1/invites/" + token + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(first, RAW_PASSWORD, first + "@example.com")))
                .andExpect(status().isCreated());

        String second = uniqueUsername("second");
        createdUsernames.add(second);
        mockMvc.perform(post("/api/v1/invites/" + token + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(second, RAW_PASSWORD, second + "@example.com")))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("INVITE_INVALID"));
    }

    @Test
    void 无效的邀请_410() throws Exception {
        String username = uniqueUsername("ghost");
        mockMvc.perform(post("/api/v1/invites/not-a-real-token/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, RAW_PASSWORD, username + "@example.com")))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("INVITE_INVALID"));
    }

    @Test
    void 成员访问管理员接口_403() throws Exception {
        User member = createUser("member", Role.MEMBER);
        String accessToken = jwtService.issueAccessToken(member);

        mockMvc.perform(post("/api/v1/admin/invites")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@example.com\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 未登录访问管理员接口_401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/invites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@example.com\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    private User createUser(String prefix, Role role) {
        String username = uniqueUsername(prefix);
        createdUsernames.add(username);
        return userRepository.saveAndFlush(User.create(
                username, username + "@example.com", passwordEncoder.encode(RAW_PASSWORD), role));
    }

    private String createInvite(User admin, String email, String role) throws Exception {
        String accessToken = jwtService.issueAccessToken(admin);
        String body = "{\"email\":" + (email == null ? "null" : "\"" + email + "\"")
                + ",\"role\":\"" + role + "\"}";
        String response = mockMvc.perform(post("/api/v1/admin/invites")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.token");
    }

    private static String uniqueUsername(String prefix) {
        String name = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        return name.toLowerCase();
    }

    private static String registerJson(String username, String password, String email) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\","
                + "\"email\":" + (email == null ? "null" : "\"" + email + "\"") + "}";
    }
}
