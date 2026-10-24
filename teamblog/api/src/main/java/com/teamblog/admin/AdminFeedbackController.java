package com.teamblog.admin;

import com.teamblog.auth.AuthPrincipal;
import com.teamblog.common.ApiResponse;
import com.teamblog.feedback.FeedbackService;
import com.teamblog.feedback.dto.FeedbackResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 管理端留言箱。
 *
 * <p>路径在 {@code /api/v1/admin/**} 下，安全配置已限制为 ADMIN（S-02），
 * 所以这里不需要再判断角色。
 */
@RestController
@RequestMapping("/api/v1/admin/feedback")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    public AdminFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public ApiResponse<List<FeedbackResponse>> list(@RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(feedbackService.list(status, limit));
    }

    @GetMapping("/count")
    public ApiResponse<Map<String, Long>> countNew() {
        return ApiResponse.ok(Map.of("new", feedbackService.countNew()));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        feedbackService.markRead(principal.id(), id);
        return ApiResponse.ok(null);
    }
}
