package com.teamblog.feedback;

import com.teamblog.common.ApiResponse;
import com.teamblog.feedback.dto.FeedbackCreateRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 访客留言（公开）。
 *
 * <p>无论是否真的存下来，一律返回 201。蜜罐命中时不能返回 4xx —— 那等于告诉机器人
 * "你被识别了"，它下次就换个填法。
 */
@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ApiResponse<Void> submit(@Valid @RequestBody FeedbackCreateRequest request,
                                    HttpServletRequest httpRequest) {
        feedbackService.submit(request, clientKey(httpRequest));
        return ApiResponse.ok(null);
    }

    /**
     * 限流的键：优先取代理头里的真实 IP（生产走 Nginx），否则用连接地址。
     *
     * <p>这里只用于"区分来源"，不用于身份识别；存库时还会再哈希一次。
     */
    static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
