package com.teamblog.checkin;

import com.teamblog.common.UuidUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 一次打卡，映射 {@code checkins}。
 *
 * <p>{@code checkin_date} 是**上海时区的业务日期**，不是时间戳（J-14）。
 * 唯一约束 {@code uq_checkins_user_date} 是"每天只能打一次"的最终保证——
 * 前端置灰只是体验，并发连点靠数据库挡。
 */
@Entity
@Table(name = "checkins")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Checkin {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "checkin_date", nullable = false, updatable = false)
    private LocalDate checkinDate;

    @Column(length = 200)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static Checkin create(UUID userId, LocalDate checkinDate, String note) {
        Checkin checkin = new Checkin();
        checkin.id = UuidUtils.v7();
        checkin.userId = Objects.requireNonNull(userId, "userId 不能为空");
        checkin.checkinDate = Objects.requireNonNull(checkinDate, "checkinDate 不能为空");
        checkin.note = note;
        return checkin;
    }
}
