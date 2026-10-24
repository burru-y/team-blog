package com.teamblog.project;

import com.teamblog.auth.JwtService;
import com.teamblog.project.dto.ProjectCreateRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 成果的发布、编辑、下架（批次 3.1）。
 *
 * <p>核心验收：**成员 A 改成员 B 的成果 = 403**。这是 S-01 的第三重（资源归属），
 * 也是 URL 级权限规则表达不了的那一层。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProjectIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Test
    void 成员发布项目_201且返回详情() throws Exception {
        User owner = persistUser("proj_owner", Role.MEMBER);

        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("我的第一个项目")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("PROJECT"))
                .andExpect(jsonPath("$.data.ownerUsername").value(owner.getUsername()));
    }

    @Test
    void 项目类缺仓库链接_422而不是500() throws Exception {
        User owner = persistUser("proj_no_link", Role.MEMBER);

        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PROJECT","title":"没链接","description":"说明","contentMd":"# 正文"}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("RULE_VIOLATED"));
    }

    @Test
    void 证书只要有图片就能发布() throws Exception {
        User owner = persistUser("proj_cert", Role.MEMBER);

        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"CERTIFICATE","title":"某证书","description":"考下来了",
                                 "imageUrl":"https://example.com/cert.png"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("CERTIFICATE"));
    }

    @Test
    void 成员改他人的成果_403() throws Exception {
        User owner = persistUser("proj_a", Role.MEMBER);
        User intruder = persistUser("proj_b", Role.MEMBER);
        UUID projectId = createProject(owner, "别人的项目");

        mockMvc.perform(put("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + token(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"我改的","description":"改说明",
                                 "githubUrl":"https://example.com/x","contentMd":"# x"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 管理员可以改他人的成果_200() throws Exception {
        User owner = persistUser("proj_c", Role.MEMBER);
        User admin = persistUser("proj_admin", Role.ADMIN);
        UUID projectId = createProject(owner, "被管理员整理");

        mockMvc.perform(put("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + token(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"管理员改过","description":"说明",
                                 "githubUrl":"https://example.com/x","contentMd":"# x"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("管理员改过"));
    }

    @Test
    void 本人可以改自己的成果_200() throws Exception {
        User owner = persistUser("proj_d", Role.MEMBER);
        UUID projectId = createProject(owner, "原标题");

        mockMvc.perform(put("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + token(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"新标题","description":"说明",
                                 "githubUrl":"https://example.com/x","contentMd":"# x"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("新标题"));
    }

    @Test
    void 下架后_详情404_列表不含_但数据还在库里() throws Exception {
        User owner = persistUser("proj_e", Role.MEMBER);
        UUID projectId = createProject(owner, "将被下架");

        mockMvc.perform(delete("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + token(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/projects/" + projectId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/projects").param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.title == '将被下架')]").doesNotExist());

        // 数据没有被删除，只是状态变了（D-13）
        Project stored = projectRepository.findById(projectId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ProjectStatus.REMOVED);
        assertThat(stored.getRemovedAt()).isNotNull();
    }

    private UUID createProject(User owner, String title) {
        return projectService.create(owner.getId(),
                new ProjectCreateRequest("PROJECT", title, "说明",
                        "https://example.com/repo", "# 正文", null)).id();
    }

    private static String projectJson(String title) {
        return """
                {"type":"PROJECT","title":"%s","description":"说明",
                 "githubUrl":"https://example.com/repo","contentMd":"# 正文"}
                """.formatted(title);
    }

    private String token(User user) {
        return jwtService.issueAccessToken(user);
    }

    private User persistUser(String prefix, Role role) {
        String username = (prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 4)).toLowerCase();
        return userRepository.saveAndFlush(User.create(
                username, username + "@example.com", passwordEncoder.encode("S3cret!pass"), role));
    }
}
