package com.teamblog.upload;

import com.teamblog.common.ApiResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 帮助资料的读写。
 *
 * <p>读走公开路径 {@code /api/v1/help/files}；写走管理员域
 * {@code /api/v1/admin/help/files}——本项目没有开启方法级安全，
 * 权限全靠 SecurityConfig 的 URL 规则，写接口放在 /admin 下才真的受保护。
 * 早期版本把写接口也放在 /help 下并用 @PreAuthorize 标注，结果注解不生效，
 * 普通成员也能调用（测试用 500 而不是 403 暴露了这一点）。
 */
@RestController
public class HelpFileController {

    private final HelpFileService service;

    public HelpFileController(HelpFileService service) {
        this.service = service;
    }

    /** 公开列表：帮助页的下载区。 */
    @GetMapping("/api/v1/help/files")
    public ApiResponse<List<HelpFileResponse>> list() {
        return ApiResponse.ok(service.list().stream().map(HelpFileResponse::from).toList());
    }

    /** 管理员上传。 */
    @PostMapping(value = "/api/v1/admin/help/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<HelpFileResponse>> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(HelpFileResponse.from(service.upload(file))));
    }

    /** 管理员删除。 */
    @DeleteMapping("/api/v1/admin/help/files")
    public ApiResponse<Void> delete(@RequestParam String name) {
        service.delete(name);
        return ApiResponse.ok(null);
    }

    /** 列表项：key 用于删除，url 直接给前端下载。 */
    public record HelpFileResponse(String name, String url, long size, String updatedAt) {
        static HelpFileResponse from(OssStorage.StoredObject object) {
            String name = object.key().startsWith("help/")
                    ? object.key().substring("help/".length())
                    : object.key();
            return new HelpFileResponse(name, object.url(), object.size(), object.updatedAt());
        }
    }
}
