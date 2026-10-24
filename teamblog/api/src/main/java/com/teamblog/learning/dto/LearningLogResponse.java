package com.teamblog.learning.dto;

import com.teamblog.learning.LearningLog;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LearningLogResponse(
        UUID id,
        String type,
        String title,
        String contentMd,
        String summaryMd,
        LocalDate logDate,
        OffsetDateTime createdAt,
        String ownerUsername
) {

    public static LearningLogResponse from(LearningLog log, String ownerUsername) {
        return new LearningLogResponse(
                log.getId(),
                log.getType().name(),
                log.getTitle(),
                log.getContentMd(),
                log.getSummaryMd(),
                log.getLogDate(),
                log.getCreatedAt(),
                ownerUsername);
    }
}
