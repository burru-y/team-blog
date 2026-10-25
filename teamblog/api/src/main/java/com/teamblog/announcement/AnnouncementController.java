package com.teamblog.announcement;

import com.teamblog.common.ApiResponse;
import com.teamblog.auth.AuthPrincipal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 公告的读写。
 *
 * <p>读在公开路径；写放在 /api/v1/admin/** 下，靠 SecurityConfig 的 URL 规则保护
 * （项目没有开启方法级安全，@PreAuthorize 不生效——见帮助资料那次的教训）。
 */
@RestController
public class AnnouncementController {

    private final AnnouncementService service;

    public AnnouncementController(AnnouncementService service) {
        this.service = service;
    }

    /**
     * 公开列表。
     *
     * @param kind 可选：传 CHANGELOG 只取更新日志（版本时间线用）
     */
    @GetMapping("/api/v1/announcements")
    public ApiResponse<List<AnnouncementResponse>> list(@RequestParam(required = false) String kind) {
        if (kind != null && kind.equalsIgnoreCase("CHANGELOG")) {
            return ApiResponse.ok(service.listChangelogs());
        }
        return ApiResponse.ok(service.listAll());
    }

    @PostMapping("/api/v1/admin/announcements")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody AnnouncementService.CreateAnnouncementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(principal.id(), request)));
    }

    @DeleteMapping("/api/v1/admin/announcements/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
