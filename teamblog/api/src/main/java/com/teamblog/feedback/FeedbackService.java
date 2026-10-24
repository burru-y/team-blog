package com.teamblog.feedback;

import com.teamblog.common.BusinessException;
import com.teamblog.common.ErrorCode;
import com.teamblog.common.HashUtils;
import com.teamblog.common.RateLimitException;
import com.teamblog.feedback.dto.FeedbackCreateRequest;
import com.teamblog.feedback.dto.FeedbackResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 留言：蜜罐 + 限流 + 管理端处理。
 *
 * <p>蜜罐命中时的处理原则：**假装成功，其实丢弃**。返回 400 等于告诉机器人
 * "这个字段被检测到了"，它下次就会绕过；而 201 什么都不写，机器人无从得知。
 */
@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final FeedbackRateLimiter rateLimiter;

    public FeedbackService(FeedbackRepository feedbackRepository, FeedbackRateLimiter rateLimiter) {
        this.feedbackRepository = feedbackRepository;
        this.rateLimiter = rateLimiter;
    }

    /**
     * 提交留言（公开接口）。
     *
     * @return true 表示真的存下来了（蜜罐命中时返回 false，但调用方仍然回 201）
     */
    @Transactional
    public boolean submit(FeedbackCreateRequest request, String ipHashSource) {
        if (request.website() != null && !request.website().isBlank()) {
            // 蜜罐命中：静默丢弃
            return false;
        }

        Long retryAfter = rateLimiter.checkAndRecord(ipHashSource);
        if (retryAfter != null) {
            throw new RateLimitException(retryAfter, "留言太频繁，请稍后再试");
        }

        feedbackRepository.save(Feedback.create(
                request.name().trim(),
                isBlank(request.email()) ? null : request.email().trim(),
                request.content().trim(),
                // IP 只存哈希：留存明文属于多余的个人信息（P-14）
                HashUtils.sha256Hex(ipHashSource)));
        return true;
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> list(String statusRaw, int limit) {
        var pageable = PageRequest.of(0, Math.min(Math.max(limit, 1), 100));
        List<Feedback> rows = (statusRaw == null || statusRaw.isBlank())
                ? feedbackRepository.findAllByOrderByCreatedAtDesc(pageable)
                : feedbackRepository.findByStatusOrderByCreatedAtDesc(parseStatus(statusRaw), pageable);

        return rows.stream().map(FeedbackResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long countNew() {
        return feedbackRepository.countByStatus(FeedbackStatus.NEW);
    }

    @Transactional
    public void markRead(UUID adminId, UUID feedbackId) {
        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "留言不存在"));
        feedback.markRead(adminId);
    }

    private static FeedbackStatus parseStatus(String raw) {
        try {
            return FeedbackStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "status 只能是 NEW / READ / ARCHIVED");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
