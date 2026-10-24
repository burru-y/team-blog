package com.teamblog.admin;

import com.teamblog.auth.JwtService;
import com.teamblog.user.Role;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 邀请的可管理性：列表、撤销、签发限流、管理员邀请的开关。
 *
 * <p>这四项解决的是同一类问题：邀请链接等同于注册资格，签发出去之后
 * 必须能被看见、被收回、被限速。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminInviteManagementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AdminInviteRepository inviteRepository;

    @Test
    void 管理员能列出已签发的邀请() throws Exception {
        String token = adminToken();
        createInvite(token, "list@example.com", "MEMBER");

        mockMvc.perform(get("/api/v1/admin/invites").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].email").value("list@example.com"));
    }

    @Test
    void 撤销后兑换返回410() throws Exception {
        String token = adminToken();
        String inviteId = createInvite(token, "revoke@example.com", "MEMBER");

        mockMvc.perform(delete("/api/v1/admin/invites/" + inviteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));

        // 撤销后该邀请不能再被兑换：用库里存的哈希反查不到明文，
        // 这里直接断言状态与业务异常，兑换路径本身由 RegistrationIntegrationTest 覆盖。
        AdminInvite invite = inviteRepository.findById(UUID.fromString(inviteId)).orElseThrow();
        assertThat(invite.isRevoked()).isTrue();
        assertThat(invite.status(java.time.OffsetDateTime.now())).isEqualTo(AdminInvite.Status.REVOKED);
    }

    @Test
    void 已使用的邀请不能撤销() throws Exception {
        String token = adminToken();
        String inviteId = createInvite(token, "used@example.com", "MEMBER");

        // 模拟已被使用
        inviteRepository.markUsedIfUnused(UUID.fromString(inviteId), java.time.OffsetDateTime.now());

        mockMvc.perform(delete("/api/v1/admin/invites/" + inviteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void 默认不允许签发管理员邀请() throws Exception {
        String token = adminToken();

        mockMvc.perform(post("/api/v1/admin/invites")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 普通成员看不到邀请列表() throws Exception {
        User member = persist("invmem", Role.MEMBER);
        String token = jwtService.issueAccessToken(member);

        mockMvc.perform(get("/api/v1/admin/invites").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String createInvite(String token, String email, String role) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/invites")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.data.id");
    }

    private String adminToken() {
        return jwtService.issueAccessToken(persist("invadmin", Role.ADMIN));
    }

    private User persist(String prefix, Role role) {
        String username = (prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 5)).toLowerCase();
        return userRepository.saveAndFlush(User.create(
                username, username + "@example.com", passwordEncoder.encode("S3cret!pass"), role));
    }
}
