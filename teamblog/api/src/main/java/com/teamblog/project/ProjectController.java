package com.teamblog.project;

import com.teamblog.auth.AuthPrincipal;
import com.teamblog.common.ApiResponse;
import com.teamblog.common.PageResponse;
import com.teamblog.project.dto.ProjectCardResponse;
import com.teamblog.project.dto.ProjectCreateRequest;
import com.teamblog.project.dto.ProjectResponse;
import com.teamblog.project.dto.ProjectUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 成果端点。
 *
 * <p>读接口公开（访客能看成果流），写接口需要登录；"只能改自己的"这一层在 Service 里，
 * 因为 URL 规则表达不了资源归属（S-01）。
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ProjectCardResponse>> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String author) {
        return ApiResponse.ok(projectService.list(cursor, limit, author));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectResponse> detail(@PathVariable UUID id) {
        return ApiResponse.ok(projectService.detail(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ProjectCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(projectService.create(principal.id(), request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProjectResponse> update(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable UUID id,
                                               @Valid @RequestBody ProjectUpdateRequest request) {
        return ApiResponse.ok(projectService.update(principal.id(), principal.role(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        projectService.remove(principal.id(), principal.role(), id);
        return ApiResponse.ok(null);
    }
}
