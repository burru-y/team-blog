package com.teamblog.checkin;

import com.teamblog.auth.JwtService;
import com.teamblog.points.PointEventRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 打卡的端到端路径（批次 4.1）。
 *
 * <p>单元测试里"重复打卡"是 mock 出来的异常；这里要验证**真实数据库的唯一约束**
 * 确实会把它翻译成 409，而不是 500。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckinIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CheckinRepository checkinRepository;

    @Autowired
    private PointEventRepository pointEventRepository;

    /**
     * 不能加 @Transactional：重复打卡会让 PostgreSQL 中止整个事务
     * （"当前事务被终止"），后续断言全都查不了。
     * 每个请求用自己的事务才和线上一致，代价是要手动清理。
     */
    @AfterEach
    void cleanUp() {
        checkinRepository.deleteAll();
        pointEventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 首次打卡201_再打409_总分只加一次() throws Exception {
        User user = persistUser();
        String token = jwtService.issueAccessToken(user);

        mockMvc.perform(post("/api/v1/checkins")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"今天学了 JPA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.streak").value(1))
                .andExpect(jsonPath("$.data.gained").value(1));

        mockMvc.perform(post("/api/v1/checkins")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_CHECKED_IN"));

        assertThat(userRepository.findTotalPointsById(user.getId())).contains(1);
    }

    @Test
    void 状态接口反映今天已打卡() throws Exception {
        User user = persistUser();
        String token = jwtService.issueAccessToken(user);

        mockMvc.perform(get("/api/v1/checkins/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkedInToday").value(false));

        mockMvc.perform(post("/api/v1/checkins")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/checkins/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkedInToday").value(true))
                .andExpect(jsonPath("$.data.streak").value(1))
                .andExpect(jsonPath("$.data.level").value(1));
    }

    @Test
    void 热力图匿名可读_含今天() throws Exception {
        User user = persistUser();
        mockMvc.perform(post("/api/v1/checkins")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/stats/heatmap").param("username", user.getUsername()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").isMap());
    }

    private User persistUser() {
        String username = ("ck" + UUID.randomUUID().toString().replace("-", "").substring(0, 6)).toLowerCase();
        return userRepository.saveAndFlush(User.create(
                username, username + "@example.com", passwordEncoder.encode("S3cret!pass"), Role.MEMBER));
    }
}
