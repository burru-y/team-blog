package com.teamblog.auth;

import com.teamblog.common.HashUtils;
import com.teamblog.common.UuidUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 一次长期会话（refresh token），映射 {@code refresh_tokens} 表。
 *
 * <p>为什么存哈希而不是明文：库被拖走时，明文 refresh token 等于"所有人的账号"。
 * 这里只存 SHA-256，明文只在签发的那一瞬间写进 Cookie（S-04）。
 *
 * <p>为什么要 {@code replaced_by_id}：轮换之后要知道"这张旧票被谁换掉了"。
 * 当一张已作废的票再次出现，就说明有人在重放——这是发现 token 泄漏的唯一信号。
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    private UUID id;

    /** 只存 id 不建关联：刷新时不需要用户对象，多一次 join 没有意义。 */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** sha256 十六进制，64 字符，与列宽一致。 */
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    /** 只留哈希，不存明文 IP（P-14）。 */
    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static RefreshToken issue(UUID userId, String tokenHash, OffsetDateTime now, Duration ttl) {
        RefreshToken token = new RefreshToken();
        token.id = UuidUtils.v7();
        token.userId = Objects.requireNonNull(userId, "userId 不能为空");
        token.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash 不能为空");
        token.expiresAt = now.plus(ttl);
        token.ipHash = null;
        return token;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(OffsetDateTime now) {
        return !expiresAt.isAfter(now);
    }

    /** 轮换：作废当前票，并记录它被哪张新票替换。 */
    public void replaceWith(RefreshToken replacement, OffsetDateTime now) {
        this.revokedAt = Objects.requireNonNull(now, "时间不能为空");
        this.replacedById = replacement.id;
    }

    public void revoke(OffsetDateTime now) {
        if (this.revokedAt == null) {
            this.revokedAt = Objects.requireNonNull(now, "时间不能为空");
        }
    }

    /** 与 {@link #issue} 配合：库里只落哈希。 */
    public static String hash(String rawToken) {
        return HashUtils.sha256Hex(rawToken);
    }
}
