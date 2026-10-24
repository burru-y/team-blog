package com.teamblog.upload;

import com.teamblog.common.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传端点。两者都需要登录（安全配置里默认 authenticated）。
 *
 * <p>为什么图片和代码分成两个接口：处理方式完全不同——图片要落盘并返回 URL，
 * 代码只要转成 Markdown 文本。塞进一个接口会让返回类型变成"二选一"，前端还得 if。
 */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    /** 图片：校验文件头 → 随机文件名落盘 → 返回可访问 URL。 */
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadService.StoredImage> uploadImage(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(uploadService.storeImage(file));
    }

    /** 代码文件：按扩展名识别语言，转成 Markdown 代码块返回（不落盘）。 */
    @PostMapping(value = "/code", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadService.CodeBlock> uploadCode(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(uploadService.toCodeBlock(file));
    }
}
