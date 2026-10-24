package com.teamblog.feedback.dto;

import com.teamblog.feedback.Feedback;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 管理端看到的留言。邮箱等联系信息只给管理员，不对外。 */
public record FeedbackResponse(
        UUID id,
        String name,
        String email,
        String content,
        String status,
        OffsetDateTime createdAt
) {

    public static FeedbackResponse from(Feedback feedback) {
        return new FeedbackResponse(
                feedback.getId(),
                feedback.getName(),
                feedback.getEmail(),
                feedback.getContent(),
                feedback.getStatus().name(),
                feedback.getCreatedAt());
    }
}
