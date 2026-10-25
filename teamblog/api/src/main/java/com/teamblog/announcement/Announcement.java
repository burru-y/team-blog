package com.teamblog.announcement;

import com.teamblog.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 公告 / 更新日志。
 *
 * <p>两种类型共用一张表：读取方式一样（置顶 + 时间倒序），
 * 拆表只会让「首页取最新一条」变成两次查询再合并。
 */
@Entity
@Table(name = "announcements")
public class Announcement {

    /** 公告类型。NOTICE 是临时通知，CHANGELOG 是版本更新记录。 */
    public enum Kind {
        NOTICE,
        CHANGELOG
    }

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(length = 20)
    private String version;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "body_md", nullable = false, columnDefinition = "text")
    private String bodyMd;

    @Column(name = "is_pinned", nullable = false)
    private boolean pinned;

    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "author_id")
    private UUID authorId;

    protected Announcement() {
    }

    private Announcement(UUID id, Kind kind, String version, String title, String bodyMd,
                         boolean pinned, UUID authorId, OffsetDateTime now) {
        this.id = id;
        this.kind = kind;
        this.version = version;
        this.title = title;
        this.bodyMd = bodyMd;
        this.pinned = pinned;
        this.authorId = authorId;
        this.publishedAt = now;
        this.createdAt = now;
    }

    public static Announcement create(UUID id, Kind kind, String version, String title,
                                      String bodyMd, boolean pinned, UUID authorId,
                                      OffsetDateTime now) {
        return new Announcement(id, kind, version, title, bodyMd, pinned, authorId, now);
    }

    public UUID getId() {
        return id;
    }

    public Kind getKind() {
        return kind;
    }

    public String getVersion() {
        return version;
    }

    public String getTitle() {
        return title;
    }

    public String getBodyMd() {
        return bodyMd;
    }

    public boolean isPinned() {
        return pinned;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public UUID getAuthorId() {
        return authorId;
    }
}
