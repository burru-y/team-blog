package com.teamblog.user;

import com.teamblog.auth.JwtService;
import com.teamblog.project.ProjectRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 个人资料修改与作者筛选（2026-10）。
 *
 * <p>两个改动：`PATCH /me` 让成员能改自己的简介，`GET /projects?author=` 让个人页
 * 能列出该成员的成果。前者要防止改到别人，后者要防止把已下架的成果也列出来。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProfileUpdateIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Test
    void 修改简介只覆盖传入字段() throws Exception {
        User user = persist("bio", Role.MEMBER);
        String token = jwtService.issueAccessToken(user);

        // 先设全三个字段
        mockMvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bio":"写后端","githubUrl":"https://github.com/bio","emailPublic":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("写后端"));

        // 只改简介，其余两个字段应保持原值
        mockMvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bio":"改成了前端"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("改成了前端"))
                .andExpect(jsonPath("$.data.githubUrl").value("https://github.com/bio"));
    }

    @Test
    void 空字符串表示清空() throws Exception {
        User user = persist("clear", Role.MEMBER);
        String token = jwtService.issueAccessToken(user);

        mockMvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"有内容\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").doesNotExist());
    }

    @Test
    void 非法链接被拒() throws Exception {
        User user = persist("badurl", Role.MEMBER);

        mockMvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"githubUrl\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 未登录不能改资料() throws Exception {
        mockMvc.perform(patch("/api/v1/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 成果列表可按作者筛选() throws Exception {
        User author = persist("authorx", Role.MEMBER);
        User other = persist("otherx", Role.MEMBER);
        createProject(author, jwtService.issueAccessToken(author));
        createProject(other, jwtService.issueAccessToken(other));

        mockMvc.perform(get("/api/v1/projects").param("author", author.getUsername()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].ownerUsername").value(author.getUsername()));
    }

    @Test
    void 不存在的作者返回空页而不是404() throws Exception {
        mockMvc.perform(get("/api/v1/projects").param("author", "nobody_here_" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    private void createProject(User owner, String token) throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PROJECT","title":"t","description":"d",
                                 "contentMd":"c","githubUrl":"https://example.com/repo"}
                                """))
                .andExpect(status().isCreated());
    }

    private User persist(String prefix, Role role) {
        String username = (prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 5)).toLowerCase();
        return userRepository.saveAndFlush(User.create(
                username, username + "@example.com", passwordEncoder.encode("S3cret!pass"), role));
    }
}
