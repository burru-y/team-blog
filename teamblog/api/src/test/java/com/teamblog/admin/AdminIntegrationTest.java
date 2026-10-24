package com.teamblog.admin;

import com.teamblog.auth.JwtService;
import com.teamblog.project.Project;
import com.teamblog.project.ProjectRepository;
import com.teamblog.project.ProjectStatus;
import com.teamblog.project.ProjectType;
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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端：成员列表、改角色、成果列表（批次 6.3 的后端部分）。
 *
 * <p>重点验证两件事：改角色会 **bump tokenVersion**（旧令牌立刻失效），
 * 以及管理端成果列表**能看到已下架的**。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminIntegrationTest {

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
    void 管理员看成员列表_含等级() throws Exception {
        User admin = persist("adm", Role.ADMIN);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void 改角色后_旧令牌立刻失效() throws Exception {
        User admin = persist("adm", Role.ADMIN);
        User member = persist("mem", Role.MEMBER);

        String memberToken = jwtService.issueAccessToken(member);

        // 改角色之前：旧令牌能访问 /me
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/admin/users/" + member.getId() + "/role")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        // 改角色之后：同一个旧令牌必须失效（tokenVersion 变了）
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_REVOKED"));
    }

    @Test
    void 不能修改自己的角色() throws Exception {
        User admin = persist("adm", Role.ADMIN);

        mockMvc.perform(patch("/api/v1/admin/users/" + admin.getId() + "/role")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("RULE_VIOLATED"));
    }

    @Test
    void 管理端成果列表_能看到已下架的() throws Exception {
        User admin = persist("adm", Role.ADMIN);
        User owner = persist("own", Role.MEMBER);

        Project removed = Project.create(owner.getId(), ProjectType.PROJECT, "已下架成果", "说明",
                "https://example.com/x", "# 正文", null);
        removed.remove(OffsetDateTime.now());
        projectRepository.saveAndFlush(removed);

        mockMvc.perform(get("/api/v1/admin/projects")
                        .param("status", "REMOVED")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.title == '已下架成果')]").exists())
                .andExpect(jsonPath("$.data[0].status").value("REMOVED"));

        // 前台列表里没有它（软下架语义）
        mockMvc.perform(get("/api/v1/projects").param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.title == '已下架成果')]").doesNotExist());
    }

    @Test
    void 成员访问管理端_403() throws Exception {
        User member = persist("mem", Role.MEMBER);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(member)))
                .andExpect(status().isForbidden());
    }

    private User persist(String prefix, Role role) {
        String username = (prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 5)).toLowerCase();
        return userRepository.saveAndFlush(User.create(
                username, username + "@example.com", passwordEncoder.encode("S3cret!pass"), role));
    }
}
