package com.teamblog.auth;

import com.teamblog.user.Role;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 过滤器在真实过滤链里的行为（1.2 的另一个验收：有效 token 200，篡改后 401）。
 *
 * <p>为什么用 MockMvc 而不是 Service 单测：token 校验只是过滤器的一半，另一半是
 * "它挂在过滤链的什么位置、异常时谁写响应"。这些只有走完整链路才验证得到。
 *
 * <p>@Transactional：每个用例自己造数据并在结束时回滚，不依赖种子（Q-09）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JwtFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void 不带token访问受保护端点_401未登录() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 有效token_200并返回当前用户() throws Exception {
        User user = persistMember("member1");
        String token = jwtService.issueAccessToken(user);

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.username").value("member1"))
                .andExpect(jsonPath("$.data.role").value("MEMBER"));
    }

    @Test
    void 篡改token_401() throws Exception {
        User user = persistMember("member2");
        String token = jwtService.issueAccessToken(user);
        String tampered = "x" + token.substring(1);

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 改密码后旧token_401会话已失效() throws Exception {
        User user = persistMember("member3");
        String token = jwtService.issueAccessToken(user);

        // 模拟"另一个设备上改了密码"：token_version +1
        user.changePassword(passwordEncoder.encode("NewPass!2026"));
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_REVOKED"))
                .andExpect(jsonPath("$.error.message").value("会话已失效，请重新登录"));
    }

    @Test
    void 用户被软删后旧token_401() throws Exception {
        User user = persistMember("member4");
        String token = jwtService.issueAccessToken(user);

        user.softDelete(OffsetDateTime.now());
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 健康检查_无需token() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
    }

    private User persistMember(String username) {
        return userRepository.saveAndFlush(User.create(
                username, username + "@example.com", passwordEncoder.encode("S3cret!pass"), Role.MEMBER));
    }
}
