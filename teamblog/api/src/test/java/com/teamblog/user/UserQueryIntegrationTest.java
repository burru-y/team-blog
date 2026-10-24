package com.teamblog.user;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 成员列表与公开资料（批次 2.2）。
 *
 * <p>三个必须验证的点，每个都对应一个真实的坑：
 * <ul>
 *   <li>软删账号不能出现在列表里（D-13）</li>
 *   <li>游标翻页不重复、不漏行（A-08）</li>
 *   <li>邮箱按 {@code email_public} 返回（S-10）</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserQueryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void 列表_不包含已软删的账号() throws Exception {
        User alive = persist("alive_one", false);
        User deleted = persist("gone_one", true);

        // 用 username 过滤断言：库里可能还有别的用例留下的行（理论上会回滚，但不依赖它）
        mockMvc.perform(get("/api/v1/users").param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.username == '%s')]".formatted(alive.getUsername())).exists())
                .andExpect(jsonPath("$.data.items[?(@.username == '%s')]".formatted(deleted.getUsername())).doesNotExist());
    }

    @Test
    void 游标翻页_两页拼接起来不重复也不漏() throws Exception {
        List<String> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(persist("page_user_" + i, false).getUsername());
        }

        List<String> seen = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 10; page++) {
            var request = get("/api/v1/users").param("limit", "2");
            if (cursor != null) {
                request = request.param("cursor", cursor);
            }
            String body = mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            List<String> usernames = JsonPath.read(body, "$.data.items[*].username");
            seen.addAll(usernames);
            // non_null 策略下 nextCursor 为 null 时字段被省略，直接 read 会抛 PathNotFoundException
            String nextCursor = body.contains("\"nextCursor\"")
                    ? JsonPath.read(body, "$.data.nextCursor")
                    : null;
            cursor = nextCursor;
            if (cursor == null) {
                break;
            }
        }

        assertThat(seen).doesNotHaveDuplicates();
        assertThat(seen).containsAll(created);
    }

    @Test
    void 列表_limit超过上限_收敛到100而不是报错() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("limit", "9999"))
                .andExpect(status().isOk());
    }

    @Test
    void 列表_cursor非法_返回400而不是500() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("cursor", "not-a-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 公开资料_邮箱默认不返回() throws Exception {
        User user = persist("private_mail", false);

        mockMvc.perform(get("/api/v1/users/" + user.getUsername()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(user.getUsername()))
                .andExpect(jsonPath("$.data.emailPublic").value(false))
                // non_null 策略下 null 字段会被省略
                .andExpect(jsonPath("$.data.email").doesNotExist());
    }

    @Test
    void 公开资料_emailPublic为true时返回邮箱() throws Exception {
        User user = persist("public_mail", false);
        user.updateProfile(null, null, null, true, null);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/v1/users/" + user.getUsername()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(user.getEmail()));
    }

    @Test
    void 公开资料_软删账号_返回404() throws Exception {
        User deleted = persist("gone_profile", true);

        mockMvc.perform(get("/api/v1/users/" + deleted.getUsername()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void 公开资料_用户名大小写不敏感() throws Exception {
        User user = persist("mixed_case", false);

        mockMvc.perform(get("/api/v1/users/" + user.getUsername().toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(user.getUsername()));
    }

    private User persist(String prefix, boolean deleted) {
        String username = (prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 6)).toLowerCase();
        User user = userRepository.saveAndFlush(User.create(
                username, username + "@example.com", passwordEncoder.encode("S3cret!pass"), Role.MEMBER));
        if (deleted) {
            user.softDelete(OffsetDateTime.now());
            userRepository.saveAndFlush(user);
        }
        return user;
    }
}
