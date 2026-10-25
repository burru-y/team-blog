package com.teamblog.upload;

import com.teamblog.common.BusinessException;
import com.teamblog.common.ErrorCode;
import com.teamblog.common.UuidUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * 文件校验与落盘（S-05）。
 *
 * <p>最核心的一条：**文件类型看内容，不看扩展名**。
 * 把 `virus.exe` 改名成 `cat.png` 是零成本操作；扩展名只是用户的声明，
 * 文件头（magic bytes）才是事实。
 *
 * <p>为什么禁 SVG：SVG 是 XML，可以内嵌 `&lt;script&gt;`。它会被浏览器当图片渲染，
 * 但脚本照样执行——典型的存储型 XSS 载体。宁可少支持一种格式。
 */
@Service
public class UploadService {

    /** 允许的图片类型 → 落盘扩展名。键来自文件头，不是客户端文件名。 */
    private static final Map<String, String> IMAGE_SIGNATURES_TO_EXT = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/gif", "gif",
            "image/webp", "webp"
    );

    /** 代码文件扩展名 → Markdown 代码块的语言标记。 */
    private static final Map<String, String> CODE_LANGUAGES = Map.ofEntries(
            Map.entry("java", "java"),
            Map.entry("py", "python"),
            Map.entry("js", "javascript"),
            Map.entry("ts", "typescript"),
            Map.entry("tsx", "tsx"),
            Map.entry("sql", "sql"),
            Map.entry("json", "json"),
            Map.entry("yml", "yaml"),
            Map.entry("yaml", "yaml"),
            Map.entry("sh", "bash"),
            Map.entry("kt", "kotlin"),
            Map.entry("go", "go"),
            Map.entry("rs", "rust"),
            Map.entry("c", "c"),
            Map.entry("cpp", "cpp"),
            Map.entry("cs", "csharp"),
            Map.entry("rb", "ruby"),
            Map.entry("php", "php"),
            Map.entry("md", "markdown"),
            Map.entry("txt", "text")
    );

    private final UploadProperties properties;
    private final OssStorage oss;

    public UploadService(UploadProperties properties, OssStorage oss) {
        this.properties = properties;
        this.oss = oss;
    }

    /** 存图片，返回可访问的相对 URL（生产由 Nginx 直接服务这个目录）。 */
    public StoredImage storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "文件为空");
        }
        requireWithinLimit(file.getSize(), properties.maxImageSize(), "图片");

        byte[] bytes = readBytes(file);
        String mime = detectImageMime(bytes);
        if (mime == null) {
            // 这里同时挡住了 SVG：它是文本，匹配不上任何图片签名
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "只支持 PNG / JPEG / GIF / WebP（SVG 因可内嵌脚本被禁用）");
        }

        String fileName = UuidUtils.v7() + "." + IMAGE_SIGNATURES_TO_EXT.get(mime);

        // 优先 OSS：图片与服务器解耦，换机器不用搬文件
        String remoteUrl = oss.put("uploads/" + fileName, bytes, mime);
        if (remoteUrl != null) {
            return new StoredImage(remoteUrl, mime, bytes.length);
        }

        // OSS 没配或上传失败：退回本地，保证功能不中断
        writeToDisk(fileName, bytes);
        return new StoredImage("/uploads/" + fileName, mime, bytes.length);
    }

    /** 把代码文件转成 Markdown 代码块（不落盘：内容是文本，直接塞进正文）。 */
    public CodeBlock toCodeBlock(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "文件为空");
        }
        requireWithinLimit(file.getSize(), properties.maxCodeSize(), "代码文件");

        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extensionOf(originalName);
        String language = CODE_LANGUAGES.get(extension);
        if (language == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "暂不支持该扩展名，可用：" + String.join(" / ", CODE_LANGUAGES.keySet()));
        }

        String text = new String(readBytes(file), StandardCharsets.UTF_8);
        // 代码块分隔符可能出现在内容里（比如 Markdown 里贴 Markdown），用更长的围栏
        String fence = text.contains("```") ? "````" : "```";
        String markdown = fence + language + "\n" + text.stripTrailing() + "\n" + fence;

        return new CodeBlock(language, markdown, (int) text.lines().count());
    }

    private void requireWithinLimit(long size, DataSize limit, String what) {
        if (size > limit.toBytes()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "%s超过上限 %d KB".formatted(what, limit.toKilobytes()));
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL, "读取上传文件失败");
        }
    }

    private void writeToDisk(String fileName, byte[] bytes) {
        try {
            Path directory = Path.of(properties.dir());
            Files.createDirectories(directory);
            // 文件名是服务端生成的 UUID：不用客户端文件名，避免路径穿越与覆盖
            Files.write(directory.resolve(fileName), bytes);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL, "保存文件失败");
        }
    }

    /**
     * 按文件头判断图片类型；不认识就返回 null。
     *
     * <p>这段比什么安全检查都实在：改扩展名、伪造 Content-Type 都骗不过它。
     */
    static String detectImageMime(byte[] bytes) {
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(bytes, 0x47, 0x49, 0x46, 0x38)) {   // "GIF8"
            return "image/gif";
        }
        if (startsWith(bytes, 0x52, 0x49, 0x46, 0x46)          // "RIFF"
                && bytes.length >= 12
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 落盘结果：URL 是给前端展示用的，不是文件系统路径。 */
    public record StoredImage(String url, String mime, long size) {
    }

    /** 代码块：markdown 直接拼进正文，language 供前端做语法高亮。 */
    public record CodeBlock(String language, String markdown, int lines) {
    }
}
