package com.teamblog.learning;

import com.teamblog.auth.JwtService;
import com.teamblog.learning.dto.LearningLogCreateRequest;
import com.teamblog.learning.dto.LearningLogCreatedResponse;
import com.teamblog.points.PointEventKind;
import com.teamblog.points.PointEventRepository;
import com.teamblog.user.Role;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import jakarta.persistence.EntityManager;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 学习内容与每日计分上限（批次 3.2）。
 *
 * <p>验收：**第 4 篇当天入库但 point_events 无新增**。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LearningLogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /** AI 客户端用 MockBean：集成测试不依赖 AI 容器是否在跑。 */
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.teamblog.ai.AiSummaryClient aiSummaryClient;

    @Autowired
    private LearningLogService learningLogService;

    @Autowired
    private LearningLogRepository learningLogRepository;

    @Autowired
    private PointEventRepository pointEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 当天前3篇各加10分_第4篇入库但不加分() {
        User author = persistUser("log_author");

        for (int i = 1; i <= 3; i++) {
            LearningLogCreatedResponse created = learningLogService.create(author.getId(), aNote("第 " + i + " 篇"));
            assertThat(created.counted()).as("第 %d 篇应当计分", i).isTrue();
            assertThat(created.points()).isEqualTo(10);
        }

        LearningLogCreatedResponse fourth = learningLogService.create(author.getId(), aNote("第 4 篇"));
        assertThat(fourth.counted()).isFalse();
        assertThat(fourth.points()).isZero();
        assertThat(fourth.remainingToday()).isZero();

        // 内容都在库里（4 篇）
        assertThat(learningLogRepository.countByUserIdAndLogDate(author.getId(), fourth.log().logDate()))
                .isEqualTo(4);
        // 积分只有 3 条，共 30 分
        assertThat(pointEventRepository.countByUserIdAndKind(author.getId(), PointEventKind.LEARNING_LOG))
                .isEqualTo(3);
        assertThat(pointEventRepository.findByUserIdOrderByCreatedAtDesc(author.getId()))
                .extracting(e -> e.getPoints())
                .containsOnly(10);

        // total_points 是原子 UPDATE 改的，绕过了一级缓存，要 clear 后才能读到新值
        entityManager.flush();
        entityManager.clear();
        assertThat(userRepository.findById(author.getId()).orElseThrow().getTotalPoints()).isEqualTo(30);
    }

    @Test
    void 通过接口发布_返回计分信息() throws Exception {
        User author = persistUser("log_api");

        mockMvc.perform(post("/api/v1/learning-logs")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"NOTE","title":"接口发的","contentMd":"# 正文"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.counted").value(true))
                .andExpect(jsonPath("$.data.points").value(10))
                .andExpect(jsonPath("$.data.remainingToday").value(2))
                .andExpect(jsonPath("$.data.log.ownerUsername").value(author.getUsername()));
    }

    @Test
    void 学习内容是公开的_匿名可读() throws Exception {
        User author = persistUser("log_public");
        LearningLogCreatedResponse created = learningLogService.create(author.getId(), aNote("公开内容"));

        mockMvc.perform(get("/api/v1/learning-logs/" + created.log().id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("公开内容"));

        mockMvc.perform(get("/api/v1/learning-logs").param("username", author.getUsername()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void 摘要_作者可以生成_别人403() throws Exception {
        User author = persistUser("sum_a");
        User other = persistUser("sum_b");
        LearningLogCreatedResponse created = learningLogService.create(author.getId(), aNote("要生成摘要的内容"));

        // 用 MockBean 拦住真实的 AI 调用：集成测试不该依赖外部服务
        when(aiSummaryClient.summarize(anyString())).thenReturn("这是一段摘要。");

        mockMvc.perform(post("/api/v1/learning-logs/" + created.log().id() + "/summarize")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("这是一段摘要。"));

        mockMvc.perform(post("/api/v1/learning-logs/" + created.log().id() + "/summarize")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(other)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 摘要_AI不可用时502() throws Exception {
        User author = persistUser("sum_fail");
        LearningLogCreatedResponse created = learningLogService.create(author.getId(), aNote("会失败的内容"));

        when(aiSummaryClient.summarize(anyString()))
                .thenThrow(new com.teamblog.ai.AiSummaryClient.AiUnavailableException("down"));

        mockMvc.perform(post("/api/v1/learning-logs/" + created.log().id() + "/summarize")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(author)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("UPSTREAM_FAILED"));
    }

    @Test
    void 成员改他人的学习内容_403() throws Exception {
        User author = persistUser("log_a");
        User intruder = persistUser("log_b");
        LearningLogCreatedResponse created = learningLogService.create(author.getId(), aNote("别人的"));

        mockMvc.perform(put("/api/v1/learning-logs/" + created.log().id())
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"我改的","contentMd":"# x"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 非法类型_400() throws Exception {
        User author = persistUser("log_bad_type");

        mockMvc.perform(post("/api/v1/learning-logs")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"VIDEO","title":"x","contentMd":"y"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    private static LearningLogCreateRequest aNote(String title) {
        return new LearningLogCreateRequest("NOTE", title, "# 正文", null);
    }

    private User persistUser(String prefix) {
        String username = (prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 4)).toLowerCase();
        return userRepository.saveAndFlush(User.create(
                username, username + "@example.com", passwordEncoder.encode("S3cret!pass"), Role.MEMBER));
    }
}
