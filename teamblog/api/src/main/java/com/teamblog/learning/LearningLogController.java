package com.teamblog.learning;

import com.teamblog.auth.AuthPrincipal;
import com.teamblog.common.ApiResponse;
import com.teamblog.learning.dto.LearningLogCreateRequest;
import com.teamblog.learning.dto.LearningLogCreatedResponse;
import com.teamblog.learning.dto.LearningLogResponse;
import com.teamblog.learning.dto.LearningLogUpdateRequest;
import com.teamblog.learning.dto.SummaryResponse;
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

    /**
     * 学习内容列表（公开）。
     *
     * <p>两种用法：
     * <ul>
     *   <li>带 username：某个人的全部笔记（个人主页用）</li>
     *   <li>不带 username：全站最新 limit 条（首页用）</li>
     * </ul>
     */
    /**
     * 笔记列表。
     *
     * <p>带 q / tag / username 任一筛选参数时走筛选查询（全部笔记）；
     * 都不带时保持旧行为：返回最新 limit 条。
     */
    @GetMapping
    public ApiResponse<List<LearningLogResponse>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Boolean all,
            @RequestParam(defaultValue = "5") int limit) {
        boolean filtering = (q != null && !q.isBlank())
                || (tag != null && !tag.isBlank())
                || Boolean.TRUE.equals(all);
        if (filtering) {
            return ApiResponse.ok(learningLogService.listFiltered(q, tag, username));
        }
        if (username == null || username.isBlank()) {
            return ApiResponse.ok(learningLogService.listLatest(limit));
        }
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

    /** 生成摘要（不落库）：前端把结果填进编辑框，成员确认后再调 PUT 保存。 */
    @PostMapping("/{id}/summarize")
    public ApiResponse<SummaryResponse> summarize(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable UUID id) {
        return ApiResponse.ok(new SummaryResponse(
                learningLogService.summarize(principal.id(), principal.role(), id)));
    }

    @PutMapping("/{id}")
    public ApiResponse<LearningLogResponse> update(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable UUID id,
                                                   @Valid @RequestBody LearningLogUpdateRequest request) {
        return ApiResponse.ok(learningLogService.update(principal.id(), principal.role(), id, request));
    }
}
