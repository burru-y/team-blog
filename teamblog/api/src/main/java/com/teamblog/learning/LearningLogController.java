package com.teamblog.learning;

import com.teamblog.auth.AuthPrincipal;
import com.teamblog.common.ApiResponse;
import com.teamblog.learning.dto.LearningLogCreateRequest;
import com.teamblog.learning.dto.LearningLogCreatedResponse;
import com.teamblog.learning.dto.LearningLogResponse;
import com.teamblog.learning.dto.LearningLogUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 学习内容端点。
 *
 * <p>读接口公开（团队博客的公开内容），写接口需要登录；归属校验在 Service。
 */
@RestController
@RequestMapping("/api/v1/learning-logs")
public class LearningLogController {

    private final LearningLogService learningLogService;

    public LearningLogController(LearningLogService learningLogService) {
        this.learningLogService = learningLogService;
    }

    /** 某人的学习内容列表（公开）。 */
    @GetMapping
    public ApiResponse<List<LearningLogResponse>> listByUser(@RequestParam String username) {
        return ApiResponse.ok(learningLogService.listByUser(username));
    }

    @GetMapping("/{id}")
    public ApiResponse<LearningLogResponse> detail(@PathVariable UUID id) {
        return ApiResponse.ok(learningLogService.detail(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LearningLogCreatedResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody LearningLogCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(learningLogService.create(principal.id(), request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<LearningLogResponse> update(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable UUID id,
                                                   @Valid @RequestBody LearningLogUpdateRequest request) {
        return ApiResponse.ok(learningLogService.update(principal.id(), principal.role(), id, request));
    }
}
