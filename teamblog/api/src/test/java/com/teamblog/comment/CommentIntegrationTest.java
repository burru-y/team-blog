package com.teamblog.comment;

import com.teamblog.auth.JwtService;
import com.teamblog.comment.dto.CommentCreateRequest;
import com.teamblog.comment.dto.CommentResponse;
import com.teamblog.project.Project;
import com.teamblog.project.ProjectRepository;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 评论：两级、软删、归属（批次 6.1）。
 *
 * <p>核心验收：**回复的回复 = 422**。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CommentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommentService commentService;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Test
    void 发表评论与回复_两级展示正确() throws Exception {
        User author = persistUser("cm_author");
        User replier = persistUser("cm_replier");
        UUID targetId = createProject(author);

        CommentResponse root = commentService.create(author.getId(),
                new CommentCreateRequest("PROJECT", targetId, null, "写得不错"));

        CommentCreateRequest replyRequest = new CommentCreateRequest("PROJECT", targetId, root.id(), "谢谢");
        mockMvc.perform(post("/api/v1/comments")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(replier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"PROJECT","targetId":"%s","parentId":"%s","content":"谢谢"}
                                """.formatted(targetId, root.id())))
                .andExpect(status().isCreated());

        List<CommentResponse> tree = commentService.list("PROJECT", targetId);
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).replies()).hasSize(1);
        assertThat(tree.get(0).replies().get(0).content()).isEqualTo("谢谢");
    }

    @Test
    void 回复的回复_422() {
        User author = persistUser("cm_depth");
        UUID targetId = createProject(author);

        CommentResponse root = commentService.create(author.getId(),
                new CommentCreateRequest("PROJECT", targetId, null, "一级"));
        CommentResponse reply = commentService.create(author.getId(),
                new CommentCreateRequest("PROJECT", targetId, root.id(), "二级"));

        assertThatThrownBy(() -> commentService.create(author.getId(),
                new CommentCreateRequest("PROJECT", targetId, reply.id(), "三级")))
                .isInstanceOf(com.teamblog.common.BusinessException.class)
                .hasMessageContaining("两级");
    }

    @Test
    void 回复的回复_通过接口返回422() throws Exception {
        User author = persistUser("cm_depth_api");
        UUID targetId = createProject(author);
        CommentResponse root = commentService.create(author.getId(),
                new CommentCreateRequest("PROJECT", targetId, null, "一级"));
        CommentResponse reply = commentService.create(author.getId(),
                new CommentCreateRequest("PROJECT", targetId, root.id(), "二级"));

        mockMvc.perform(post("/api/v1/comments")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"PROJECT","targetId":"%s","parentId":"%s","content":"三级"}
                                """.formatted(targetId, reply.id())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("RULE_VIOLATED"));
    }

    @Test
    void 软删_有回复的显示为已删除_没有回复的直接消失() throws Exception {
        User author = persistUser("cm_del");
        UUID targetId = createProject(author);

        CommentResponse withReply = commentService.create(author.getId(),
                new CommentCreateRequest("PROJECT", targetId, null, "会被删"));
        commentService.create(author.getId(),
                new CommentCreateRequest("PROJECT", targetId, withReply.id(), "我回复了"));

        CommentResponse lonely = commentService.create(author.getId(),
                new CommentCreateRequest("PROJECT", targetId, null, "孤零零"));

        commentService.delete(author.getId(), Role.MEMBER, withReply.id());
        commentService.delete(author.getId(), Role.MEMBER, lonely.id());

        List<CommentResponse> tree = commentService.list("PROJECT", targetId);
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).deleted()).isTrue();
        assertThat(tree.get(0).content()).isNull();
        assertThat(tree.get(0).replies()).hasSize(1);

        // 数据还在库里，只是打了时间戳
        assertThat(commentRepository.findById(withReply.id()).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    void 删别人的评论_403() throws Exception {
        User author = persistUser("cm_own");
        User intruder = persistUser("cm_other");
        UUID targetId = createProject(author);
        CommentResponse comment = commentService.create(author.getId(),
                new CommentCreateRequest("PROJECT", targetId, null, "我的评论"));

        mockMvc.perform(delete("/api/v1/comments/" + comment.id())
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 管理员可以删别人的评论() throws Exception {
        User author = persistUser("cm_admin_case");
        User admin = persistUser("cm_admin", Role.ADMIN);
        UUID targetId = createProject(author);
        CommentResponse comment = commentService.create(author.getId(),
                new CommentCreateRequest("PROJECT", targetId, null, "违规内容"));

        mockMvc.perform(delete("/api/v1/comments/" + comment.id())
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void 评论列表公开可读() throws Exception {
        User author = persistUser("cm_public");
        UUID targetId = createProject(author);
        commentService.create(author.getId(),
                new CommentCreateRequest("PROJECT", targetId, null, "访客也能看"));

        mockMvc.perform(get("/api/v1/comments")
                        .param("targetType", "PROJECT")
                        .param("targetId", targetId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    private UUID createProject(User owner) {
        Project project = projectRepository.saveAndFlush(Project.create(
                owner.getId(), ProjectType.PROJECT, "评论目标", "说明",
                "https://example.com/x", "# 正文", null));
        return project.getId();
    }

    private User persistUser(String prefix) {
        return persistUser(prefix, Role.MEMBER);
    }

    private User persistUser(String prefix, Role role) {
        String username = (prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 4)).toLowerCase();
        return userRepository.saveAndFlush(User.create(
                username, username + "@example.com", passwordEncoder.encode("S3cret!pass"), role));
    }
}
