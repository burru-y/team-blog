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

    @Column(length = 200)
    private String tags;

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
                                     String contentMd, String summaryMd, String tags, LocalDate logDate) {
        LearningLog log = new LearningLog();
        log.id = UuidUtils.v7();
        log.userId = Objects.requireNonNull(userId, "userId 不能为空");
        log.type = Objects.requireNonNull(type, "type 不能为空");
        log.title = Objects.requireNonNull(title, "title 不能为空");
        log.contentMd = Objects.requireNonNull(contentMd, "contentMd 不能为空");
        log.summaryMd = summaryMd;
        log.tags = normalizeTags(tags);
        log.logDate = Objects.requireNonNull(logDate, "logDate 不能为空");
        return log;
    }

    /** 写入 AI 生成的摘要。摘要是一次生成、多次读取的派生数据。 */
    public void applySummary(String summary) {
        this.summaryMd = summary;
    }

    public void updateDetails(String title, String contentMd, String summaryMd, String tags) {
        this.title = Objects.requireNonNull(title, "title 不能为空");
        this.contentMd = Objects.requireNonNull(contentMd, "contentMd 不能为空");
        this.summaryMd = summaryMd;
        this.tags = normalizeTags(tags);
    }

    /**
     * 标签归一化：去空白、去重、最多 5 个、总长不超过列宽。
     *
     * <p>存成逗号分隔字符串，所以必须在这里拦住重复与超长——
     * 否则标签云里会出现「Java」和「java 」两个条目。
     */
    static String normalizeTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        var seen = new java.util.LinkedHashSet<String>();
        for (String piece : raw.split("[,，]")) {
            String tag = piece.trim();
            if (tag.isEmpty()) {
                continue;
            }
            if (tag.length() > 20) {
                tag = tag.substring(0, 20);
            }
            seen.add(tag);
            if (seen.size() >= 5) {
                break;
            }
        }
        return seen.isEmpty() ? null : String.join(",", seen);
    }

    public String getTags() {
        return tags;
    }

    public boolean isOwnedBy(UUID candidate) {
        return userId.equals(candidate);
    }
}
