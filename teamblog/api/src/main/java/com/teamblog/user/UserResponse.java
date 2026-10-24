package com.teamblog.user;

import com.teamblog.points.LevelCalculator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 用户的对外表示（J-04：DTO 用 record，命名 XxxResponse）。
 *
 * <p>为什么不在实体上加 {@code @JsonIgnore} 了事：实体字段会随业务增长（tokenVersion、
 * passwordHash、deletedAt），漏一个注解就是一次泄露。DTO 是白名单——没写进来的字段
 * 永远不会出去（J-36）。
 *
 * <p>email 是否返回由调用方决定：本人看自己（{@code /me}）永远可见；别人看时只按
 * {@code email_public} 开关返回（S-10）。
 */
public record UserResponse(
        UUID id,
        String username,
        String email,
        String role,
        String avatarUrl,
        String bio,
        String githubUrl,
        boolean emailPublic,
        int totalPoints,
        /** 等级由总分推导（LevelCalculator）。放在后端算，前端就不必复制一份阈值表。 */
        int level,
        OffsetDateTime createdAt
) {

    /** 本人视角：返回真实邮箱。 */
    public static UserResponse from(User user) {
        return from(user, true);
    }

    /** 公开视角：{@code email_public=false} 时不带邮箱（S-10）。 */
    public static UserResponse fromPublic(User user) {
        return from(user, user.isEmailPublic());
    }

    private static UserResponse from(User user, boolean includeEmail) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                includeEmail ? user.getEmail() : null,
                user.getRole().name(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getGithubUrl(),
                user.isEmailPublic(),
                user.getTotalPoints(),
                LevelCalculator.levelOf(user.getTotalPoints()),
                user.getCreatedAt());
    }
}
