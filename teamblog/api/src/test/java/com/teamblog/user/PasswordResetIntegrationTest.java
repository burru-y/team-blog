package com.teamblog.user;

import com.teamblog.auth.JwtService;
import com.teamblog.common.BusinessException;
import com.teamblog.common.ErrorCode;
import com.teamblog.common.PasswordRules;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 密码重置（批次 8.4）。
 *
 * <p>一期没有邮件服务，忘记密码的唯一恢复路径就是管理员重置。
 * 两条硬要求：**旧会话立刻失效**、**不能改别人的不等于能改别人的密码**。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PasswordResetIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Test
    void 管理员重置密码_旧令牌立刻失效_新密码能登录() throws Exception {
        User admin = persist("pwadm", Role.ADMIN);
        User member = persist("pwmem", Role.MEMBER);
        String oldToken = jwtService.issueAccessToken(member);

        mockMvc.perform(post("/api/v1/admin/users/" + member.getId() + "/password")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NewPass#2026\"}"))
                .andExpect(status().isOk());

        // 旧 token 失效（tokenVersion +1）
        mockMvc.perform(post("/api/v1/me/password")
                        .header("Authorization", "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"x\",\"newPassword\":\"Whatever#2026\"}"))
                .andExpect(status().isUnauthorized());

        // 新密码可以登录
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"NewPass#2026\"}"
                                .formatted(member.getUsername())))
                .andExpect(status().isOk());
    }

    @Test
    void 普通成员不能重置他人密码() throws Exception {
        User member = persist("pwa", Role.MEMBER);
        User victim = persist("pwb", Role.MEMBER);

        mockMvc.perform(post("/api/v1/admin/users/" + victim.getId() + "/password")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NewPass#2026\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 太短的密码_400() throws Exception {
        User admin = persist("pwadm2", Role.ADMIN);
        User member = persist("pwmem2", Role.MEMBER);

        mockMvc.perform(post("/api/v1/admin/users/" + member.getId() + "/password")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 自己改密码_必须验证旧密码() throws Exception {
        User member = persist("pwself", Role.MEMBER);
        String token = jwtService.issueAccessToken(member);

        mockMvc.perform(post("/api/v1/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"wrong\",\"newPassword\":\"NewPass#2026\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"S3cret!pass\",\"newPassword\":\"NewPass#2026\"}"))
                .andExpect(status().isOk());

        User reloaded = userRepository.findById(member.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPass#2026", reloaded.getPasswordHash())).isTrue();
    }

    @Test
    void 密码规则_按字节限制_中文不会绕过() {
        assertThat(PasswordRules.looksValid("12345678")).isTrue();
        assertThat(PasswordRules.looksValid("1234567")).isFalse();
        // 25 个汉字 = 75 字节，超过 BCrypt 的 72
        assertThatThrownBy(() -> PasswordRules.validate("密".repeat(25), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("太长");
        // 用户名要 ≥8 位，否则先被长度规则拦下，测不到"不能等于用户名"这一条
        assertThatThrownBy(() -> PasswordRules.validate("longusername", "longusername"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名");
        assertThat(PasswordRules.looksValid("密".repeat(24))).isTrue();
    }

    @Test
    void 重置不存在的用户_404() throws Exception {
        User admin = persist("pwadm3", Role.ADMIN);

        mockMvc.perform(post("/api/v1/admin/users/" + UUID.randomUUID() + "/password")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NewPass#2026\"}"))
                .andExpect(status().isNotFound());
    }

    private User persist(String prefix, Role role) {
        String username = (prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 4)).toLowerCase();
        return userRepository.saveAndFlush(User.create(
                username, username + "@example.com", passwordEncoder.encode("S3cret!pass"), role));
    }

    /** 让编译器确认 ErrorCode 存在于断言上下文（避免误删 import）。 */
    @SuppressWarnings("unused")
    private static final ErrorCode SAMPLE = ErrorCode.VALIDATION_FAILED;
}
