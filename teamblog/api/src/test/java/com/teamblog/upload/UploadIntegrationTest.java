package com.teamblog.upload;

import com.teamblog.auth.JwtService;
import com.teamblog.user.Role;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 上传的类型校验与代码转 Markdown（批次 3.3）。
 *
 * <p>核心验收：**改后缀的 exe 被拒**、**py 文件变成 ```python 块**。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UploadIntegrationTest {

    /** 最小的 PNG 头：89 50 4E 47 0D 0A 1A 0A。 */
    private static final byte[] PNG_HEADER = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Test
    void 上传PNG_返回可访问URL() throws Exception {
        User author = persistUser();
        byte[] png = concat(PNG_HEADER, new byte[]{1, 2, 3, 4});

        mockMvc.perform(multipart("/api/v1/uploads/images")
                        .file(new MockMultipartFile("file", "cat.png", "image/png", png))
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mime").value("image/png"))
                .andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.startsWith("/uploads/")))
                .andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.endsWith(".png")));
    }

    @Test
    void 把exe改成png后缀_被文件头挡住() throws Exception {
        User author = persistUser();
        // 内容是文本（模拟 exe/脚本），但文件名和 Content-Type 都声称是图片
        byte[] fake = "MZ\u0000this is not an image".getBytes(StandardCharsets.ISO_8859_1);

        mockMvc.perform(multipart("/api/v1/uploads/images")
                        .file(new MockMultipartFile("file", "virus.png", "image/png", fake))
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(author)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void SVG被拒绝() throws Exception {
        User author = persistUser();
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/v1/uploads/images")
                        .file(new MockMultipartFile("file", "evil.svg", "image/svg+xml", svg))
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(author)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 代码文件_py转成python代码块() throws Exception {
        User author = persistUser();
        byte[] source = "def hello():\n    print('hi')\n".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/v1/uploads/code")
                        .file(new MockMultipartFile("file", "demo.py", "text/plain", source))
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.language").value("python"))
                .andExpect(jsonPath("$.data.lines").value(2))
                .andExpect(jsonPath("$.data.markdown")
                        .value(org.hamcrest.Matchers.startsWith("```python")))
                .andExpect(jsonPath("$.data.markdown")
                        .value(org.hamcrest.Matchers.containsString("def hello():")));
    }

    @Test
    void 不支持的扩展名_400() throws Exception {
        User author = persistUser();

        mockMvc.perform(multipart("/api/v1/uploads/code")
                        .file(new MockMultipartFile("file", "a.exe", "application/octet-stream",
                                new byte[]{0x4D, 0x5A}))
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(author)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 匿名上传_401() throws Exception {
        mockMvc.perform(multipart("/api/v1/uploads/images")
                        .file(new MockMultipartFile("file", "a.png", "image/png", PNG_HEADER)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 文件头识别_不认识的格式返回null() {
        assertThat(UploadService.detectImageMime("hello".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(UploadService.detectImageMime(PNG_HEADER)).isEqualTo("image/png");
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private User persistUser() {
        String username = ("up" + UUID.randomUUID().toString().replace("-", "").substring(0, 6)).toLowerCase();
        return userRepository.saveAndFlush(User.create(
                username, username + "@example.com", passwordEncoder.encode("S3cret!pass"), Role.MEMBER));
    }
}
