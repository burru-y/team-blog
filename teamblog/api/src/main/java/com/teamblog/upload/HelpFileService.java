package com.teamblog.upload;

import com.teamblog.common.BusinessException;
import com.teamblog.common.ErrorCode;

import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * 帮助资料（PDF / 压缩包 / 文档）的存取。
 *
 * <p>不做数据库表：对象本身就存在 OSS 上，列表直接从 OSS 列出来。
 * 少一张表就少一次迁移、少一处不一致——文件在不在，以 OSS 为准。
 *
 * <p>只允许有限的扩展名：这个接口会把文件公开给所有人下载，
 * 不能变成「任意文件托管」。可执行文件、脚本一律拒绝。
 */
@Service
public class HelpFileService {

    private static final List<String> ALLOWED_EXTENSIONS =
            List.of("pdf", "zip", "md", "txt", "png", "jpg", "jpeg", "docx", "xlsx", "pptx");

    private static final String PREFIX = "help/";
    private static final DataSize MAX_SIZE = DataSize.ofMegabytes(100);

    private final OssStorage oss;

    public HelpFileService(OssStorage oss) {
        this.oss = oss;
    }

    /** 公开列表：给帮助页用。 */
    public List<OssStorage.StoredObject> list() {
        return oss.list(PREFIX).stream()
                // 按上传时间倒序：最新的排前面
                .sorted((a, b) -> b.updatedAt().compareTo(a.updatedAt()))
                .toList();
    }

    /** 上传一份帮助资料，返回可下载的公开地址。 */
    public OssStorage.StoredObject upload(MultipartFile file) {
        if (!oss.enabled()) {
            throw new BusinessException(ErrorCode.INTERNAL, "未配置对象存储，无法上传帮助资料");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "文件为空");
        }
        if (file.getSize() > MAX_SIZE.toBytes()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "文件超过上限 %d MB".formatted(MAX_SIZE.toMegabytes()));
        }

        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extensionOf(original);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "不支持该类型，可用：" + String.join(" / ", ALLOWED_EXTENSIONS));
        }

        // 保留原始文件名（用户要能看懂），只做最小清洗：去掉路径分隔符与控制字符
        String safeName = original.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (safeName.isBlank()) {
            safeName = "file." + extension;
        }

        try {
            byte[] bytes = file.getBytes();
            String contentType = file.getContentType() == null
                    ? "application/octet-stream"
                    : file.getContentType();
            String url = oss.put(PREFIX + safeName, bytes, contentType);
            if (url == null) {
                throw new BusinessException(ErrorCode.INTERNAL, "上传失败，请稍后重试");
            }
            return new OssStorage.StoredObject(PREFIX + safeName, url, bytes.length, "");
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL, "读取上传文件失败");
        }
    }

    /** 按文件名删除（只允许删 help/ 前缀下的）。 */
    public void delete(String name) {
        String safeName = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (safeName.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "文件名不能为空");
        }
        oss.delete(PREFIX + safeName);
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
