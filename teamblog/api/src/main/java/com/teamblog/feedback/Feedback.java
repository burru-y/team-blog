package com.teamblog.feedback;

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
 * 访客留言，映射 {@code feedbacks}。
 *
 * <p>这是全站**唯一不需要登录的写接口**，所以三件事必须做到：
 * 蜜罐字段、按 IP 限流、IP 只存哈希（P-14：留存 IP 明文属于多余的个人信息）。
 */
@Entity
@Table(name = "feedbacks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedback {

    @Id
    private UUID id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 255)
    private String email;

    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FeedbackStatus status;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "handled_by")
    private UUID handledBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static Feedback create(String name, String email, String content, String ipHash) {
        Feedback feedback = new Feedback();
        feedback.id = UuidUtils.v7();
        feedback.name = Objects.requireNonNull(name, "name 不能为空");
        feedback.email = email;
        feedback.content = Objects.requireNonNull(content, "content 不能为空");
        feedback.status = FeedbackStatus.NEW;
        feedback.ipHash = ipHash;
        return feedback;
    }

    /** 管理员标记已读：记下是谁处理的，方便多人协作时知道谁看过。 */
    public void markRead(UUID adminId) {
        if (this.status == FeedbackStatus.NEW) {
            this.status = FeedbackStatus.READ;
            this.handledBy = adminId;
        }
    }
}
