package com.teamblog.points;

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
 * 积分流水（账本），映射 {@code point_events}。
 *
 * <p>为什么要有账本而不是直接给 {@code users.total_points} 加数字：
 * 出了争议时能回答"这 30 分是怎么来的"；对账任务能重算并比对；
 * 唯一约束还能从数据库层面挡住重复计分（{@code uq_points_once}）。
 *
 * <p>{@code ref_id} 是来源对象的 id（学习内容 id、打卡 id）。同一事件重复触发时，
 * 唯一约束会让第二次插入失败——这才是"只加一次"的最终保证，不是靠代码里的 if。
 */
@Entity
@Table(name = "point_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointEvent {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PointEventKind kind;

    @Column(name = "ref_id", nullable = false, length = 64, updatable = false)
    private String refId;

    @Column(nullable = false, updatable = false)
    private int points;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static PointEvent of(UUID userId, PointEventKind kind, String refId, int points) {
        if (points <= 0) {
            throw new IllegalArgumentException("积分必须大于 0");
        }
        PointEvent event = new PointEvent();
        event.id = UuidUtils.v7();
        event.userId = Objects.requireNonNull(userId);
        event.kind = Objects.requireNonNull(kind);
        event.refId = Objects.requireNonNull(refId);
        event.points = points;
        return event;
    }
}
