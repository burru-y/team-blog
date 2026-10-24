package com.teamblog.comment;

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

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 评论，映射 {@code comments}。
 *
 * <p>两级结构：{@code parent_id = null} 是一级评论，非空是回复。**不允许三级**——
 * 数据库上有触发器 {@code check_comment_depth} 兜底，Service 里也会先判断并返回 422。
 *
 * <p>为什么限制两级：树的递归渲染、折叠、计数都会复杂一个数量级，而团队博客的
 * 讨论规模用不上。真需要多级时，业界做法也是"楼中楼"平铺，而不是无限嵌套。
 *
 * <p>软删：{@code deleted_at} 非空表示已删。数据保留，因为有回复的评论突然消失
 * 会让下面的回复变成孤儿。
 */
@Entity
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20, updatable = false)
    private CommentTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @Column(name = "parent_id", updatable = false)
    private UUID parentId;

    @Column(nullable = false)
    private String content;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static Comment create(UUID userId, CommentTargetType targetType, UUID targetId,
                                 UUID parentId, String content) {
        Comment comment = new Comment();
        comment.id = UuidUtils.v7();
        comment.userId = Objects.requireNonNull(userId, "userId 不能为空");
        comment.targetType = Objects.requireNonNull(targetType, "targetType 不能为空");
        comment.targetId = Objects.requireNonNull(targetId, "targetId 不能为空");
        comment.parentId = parentId;
        comment.content = Objects.requireNonNull(content, "content 不能为空");
        return comment;
    }

    /** 软删：只打时间戳，内容仍在库里。 */
    public void softDelete(OffsetDateTime now) {
        if (this.deletedAt == null) {
            this.deletedAt = Objects.requireNonNull(now, "时间不能为空");
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isOwnedBy(UUID candidate) {
        return userId.equals(candidate);
    }

    public boolean isRoot() {
        return parentId == null;
    }
}
