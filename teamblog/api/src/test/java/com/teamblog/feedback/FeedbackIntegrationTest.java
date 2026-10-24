package com.teamblog.feedback;

import com.teamblog.auth.JwtService;
import com.teamblog.feedback.dto.FeedbackCreateRequest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 留言：蜜罐与限流（批次 6.2）。
 *
 * <p>两个验收点：**蜜罐有值 → 静默丢弃**（返回 201 但库里没有）、
 * **访客能提交**。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FeedbackIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Test
    void 访客能提交留言_存下来且IP只存哈希() throws Exception {
        mockMvc.perform(post("/api/v1/feedback")
                        .header("X-Forwarded-For", randomIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"访客小王","email":"wang@example.com","content":"网站有个错别字"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        assertThat(feedbackRepository.count()).isEqualTo(1);
        Feedback saved = feedbackRepository.findAll().get(0);
        assertThat(saved.getStatus()).isEqualTo(FeedbackStatus.NEW);
        assertThat(saved.getIpHash()).isNotBlank().hasSize(64);
        assertThat(saved.getIpHash()).doesNotContain("192.168");   // 不是明文 IP
    }

    @Test
    void 蜜罐有值_返回201但什么也不存() throws Exception {
        mockMvc.perform(post("/api/v1/feedback")
                        .header("X-Forwarded-For", randomIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"bot","content":"buy cheap pills","website":"http://spam.example"}
                                """))
                .andExpect(status().isOk());

        // 关键：对外说成功，库里空着——机器人无从判断自己被识破
        assertThat(feedbackRepository.count()).isZero();
    }

    @Test
    void 同一IP一小时内超过3条_第4条被限流() {
        String ip = randomIp();

        for (int i = 0; i < 3; i++) {
            feedbackService.submit(new FeedbackCreateRequest("访客" + i, null, "第 " + i + " 条", null), ip);
        }

        assertThatThrownBy(() -> feedbackService.submit(
                new FeedbackCreateRequest("访客4", null, "第 4 条", null), ip))
                .isInstanceOf(com.teamblog.common.RateLimitException.class);

        assertThat(feedbackRepository.count()).isEqualTo(3);
    }

    @Test
    void 管理员看列表并标记已读() throws Exception {
        String ip = randomIp();
        feedbackService.submit(new FeedbackCreateRequest("访客", null, "需要处理", null), ip);
        UUID feedbackId = feedbackRepository.findAll().get(0).getId();

        User admin = persistAdmin();

        mockMvc.perform(get("/api/v1/admin/feedback")
                        .param("status", "NEW")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(post("/api/v1/admin/feedback/" + feedbackId + "/read")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(admin)))
                .andExpect(status().isOk());

        Feedback updated = feedbackRepository.findById(feedbackId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(FeedbackStatus.READ);
        assertThat(updated.getHandledBy()).isEqualTo(admin.getId());
    }

    @Test
    void 普通成员看不到留言箱() throws Exception {
        User member = userRepository.saveAndFlush(User.create(
                ("fb" + UUID.randomUUID().toString().replace("-", "").substring(0, 5)).toLowerCase(),
                "fb@example.com", passwordEncoder.encode("S3cret!pass"), Role.MEMBER));

        mockMvc.perform(get("/api/v1/admin/feedback")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(member)))
                .andExpect(status().isForbidden());
    }

    private User persistAdmin() {
        String username = ("adm" + UUID.randomUUID().toString().replace("-", "").substring(0, 5)).toLowerCase();
        return userRepository.saveAndFlush(User.create(
                username, username + "@example.com", passwordEncoder.encode("S3cret!pass"), Role.ADMIN));
    }

    private static String randomIp() {
        return "10.%d.%d.%d".formatted(
                (int) (Math.random() * 255), (int) (Math.random() * 255), (int) (Math.random() * 255));
    }
}
