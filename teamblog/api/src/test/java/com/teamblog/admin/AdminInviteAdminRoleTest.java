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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 打开 invite.allow-admin-role 后，管理员可以签发 ADMIN 角色的邀请。
 *
 * <p>单独一个类：@TestPropertySource 只能标在类上，改不了单个方法。
 * 默认关闭的行为在 AdminInviteManagementTest 里验证。
 */
@SpringBootTest(properties = "invite.allow-admin-role=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminInviteAdminRoleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Test
    void 开关打开后可以签发管理员邀请() throws Exception {
        String username = "adm" + UUID.randomUUID().toString().replace("-", "").substring(0, 5);
        User admin = userRepository.saveAndFlush(User.create(
                username, username + "@example.com", passwordEncoder.encode("S3cret!pass"), Role.ADMIN));

        mockMvc.perform(post("/api/v1/admin/invites")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }
}