package com.teamblog.learning;

import com.teamblog.common.UuidUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 学习内容，映射 {@code learning_logs}。
 *
 * <p>{@code log_date} 是**业务日期**（东八区的自然日），不是 created_at。两者都要：
 * 前者用于"今天写了几篇"的计分上限判断，后者用于排序与审计。用 created_at 判断会
 * 在 23:59 与 00:01 之间出现"跨天"争议（J-14）。
 */
@Entity
@Table(name = "learning_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningLog {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "content_md", nullable = false)
    private String contentMd;

    @Column(name = "summary_md")
    private String summaryMd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LearningLogType type;

    @Column(name = "log_date", nullable = false, updatable = false)
    private LocalDate logDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static LearningLog create(UUID userId, LearningLogType type, String title,
                                     String contentMd, String summaryMd, LocalDate logDate) {
        LearningLog log = new LearningLog();
        log.id = UuidUtils.v7();
        log.userId = Objects.requireNonNull(userId, "userId 不能为空");
        log.type = Objects.requireNonNull(type, "type 不能为空");
        log.title = Objects.requireNonNull(title, "title 不能为空");
        log.contentMd = Objects.requireNonNull(contentMd, "contentMd 不能为空");
        log.summaryMd = summaryMd;
        log.logDate = Objects.requireNonNull(logDate, "logDate 不能为空");
        return log;
    }

    public void updateDetails(String title, String contentMd, String summaryMd) {
        this.title = Objects.requireNonNull(title, "title 不能为空");
        this.contentMd = Objects.requireNonNull(contentMd, "contentMd 不能为空");
        this.summaryMd = summaryMd;
    }

    public boolean isOwnedBy(UUID candidate) {
        return userId.equals(candidate);
    }
}
