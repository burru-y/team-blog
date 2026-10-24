package com.teamblog.admin;

import com.teamblog.common.UuidUtils;
import com.teamblog.user.Role;
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

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 一次性邀请，映射 {@code admin_invites} 表。
 *
 * <p>为什么存哈希不存明文：邀请链接等同于"注册资格"，泄漏一张就能拉一个人进来。
 * 库里只存 sha256，明文只在创建时返回一次（和 refresh token 同一个思路，S-04）。
 *
 * <p>为什么 {@code used_at} 而不是删除整行：用过/过期的邀请要留着，才能区分
 * "链接失效"（410 Gone）和"链接根本不存在"（404）—— 前者是用户可理解的提示，
 * 后者多半是拼错了。
 */
@Entity
@Table(name = "admin_invites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminInvite {

    @Id
    private UUID id;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    /** 可选：指定邮箱时，注册必须用这个邮箱（HR 发给特定的人）。 */
    @Column(length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    /** 撤销时间。非空表示被管理员作废，与"过期"区分开。 */
    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static AdminInvite create(UUID createdBy, String tokenHash, String email, Role role,
                                     OffsetDateTime now, Duration ttl) {
        AdminInvite invite = new AdminInvite();
        invite.id = UuidUtils.v7();
        invite.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash 不能为空");
        invite.email = email;
        invite.role = Objects.requireNonNull(role, "role 不能为空");
        invite.createdBy = Objects.requireNonNull(createdBy, "createdBy 不能为空");
        invite.expiresAt = now.plus(ttl);
        return invite;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(OffsetDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * 邀请状态。判定有优先级：已用 > 已撤销 > 已过期 > 有效。
     *
     * <p>优先级不能乱：一张已用过的邀请即便也"过期了"，对外仍应显示已使用，
     * 否则管理员会以为是自己撤销晚了。
     */
    public Status status(OffsetDateTime now) {
        if (isUsed()) {
            return Status.USED;
        }
        if (isRevoked()) {
            return Status.REVOKED;
        }
        if (isExpired(now)) {
            return Status.EXPIRED;
        }
        return Status.ACTIVE;
    }

    /** 撤销：只对未使用的邀请有意义，用过的不能被"撤回"。 */
    public void revoke(OffsetDateTime now) {
        if (isUsed()) {
            throw new IllegalStateException("已使用的邀请不能撤销");
        }
        if (this.revokedAt == null) {
            this.revokedAt = Objects.requireNonNull(now, "时间不能为空");
        }
    }

    /** 只用于"非并发"路径的语义表达；真正的原子占用在 Repository 的条件更新里。 */
    public void markUsed(OffsetDateTime now) {
        if (this.usedAt == null) {
            this.usedAt = Objects.requireNonNull(now, "时间不能为空");
        }
    }

    public enum Status {
        ACTIVE, USED, EXPIRED, REVOKED
    }
}
