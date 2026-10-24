package com.teamblog.user;

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
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 账号实体，映射 {@code users} 表（V1__init.sql）。
 *
 * <p>为什么没有 {@code updated_at}：V1 没建这一列，而 D-02 规定已执行的迁移不可修改。
 * 需要时补 V2 迁移，绝不在实体侧假装存在——{@code ddl-auto=validate} 会立刻报错。
 *
 * <p>为什么构造器是 protected 且只有一个静态工厂：主键、用户名规范化、初始积分与
 * token_version 都是不变量，散在各个 service 里各拼一次必然拼错（J-36）。
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // JPA 需要，业务代码不要用
public class User {

    @Id
    private UUID id;

    /**
     * 登录名，同时是 /u/{username} 的路由段，因此可改但有代价：旧链接会失效。
     *
     * <p>曾经是 updatable = false（当时没打算支持改名）。加改名功能时忘了这一点，
     * 结果是 JPA 静默跳过 UPDATE：接口返回新名字、库里还是旧名字。
     * 这类"内存改了、库里没改"的问题只有回查数据库才会暴露。
     */
    @Column(nullable = false, length = 20)
    private String username;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    private String bio;

    @Column(name = "github_url", length = 300)
    private String githubUrl;

    /** 显示名（昵称）。为空时接口回退到 username。 */
    @Column(name = "display_name", length = 30)
    private String displayName;

    @Column(name = "email_public", nullable = false)
    private boolean emailPublic;

    @Column(name = "total_points", nullable = false)
    private int totalPoints;

    /** 改密码、改角色、被移除时 +1，令已签发的 access token 全部失效（S-02）。 */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * 创建账号。用户名与邮箱在这里统一转小写：库里 {@code uq_users_email_lower}
     * 与 {@code ck_users_username} 是最后一道防线，不是第一道。
     */
    public static User create(String username, String email, String passwordHash, Role role) {
        User user = new User();
        user.id = UuidUtils.v7();
        user.username = normalizeUsername(username);
        user.email = normalizeEmail(email);
        user.passwordHash = requireHash(passwordHash);
        user.role = Objects.requireNonNull(role, "role 不能为空");
        user.emailPublic = false;
        user.totalPoints = 0;
        user.tokenVersion = 0;
        return user;
    }

    /** 换密码后旧 token 必须立即失效，否则改密码等于没改。 */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = requireHash(newPasswordHash);
        this.tokenVersion++;
    }

    /** 改角色同理：降级后旧 token 里的角色立刻作废。 */
    public void changeRole(Role newRole) {
        this.role = Objects.requireNonNull(newRole, "role 不能为空");
        this.tokenVersion++;
    }

    /**
     * 强制下线所有设备（不动密码）。
     *
     * <p>用于检测到 refresh token 重放时：此时必须假设 refresh token 已泄漏，
     * 只作废 refresh 不足以止损——攻击者手里那个 access token 还能用 30 分钟。
     */
    public void invalidateSessions() {
        this.tokenVersion++;
    }

    public void markLoggedIn(OffsetDateTime at) {
        this.lastLoginAt = Objects.requireNonNull(at, "登录时间不能为空");
    }

    /** 软删（D-13）：行还在，登录与查询由上层过滤。 */
    public void softDelete(OffsetDateTime at) {
        this.deletedAt = Objects.requireNonNull(at, "删除时间不能为空");
        this.tokenVersion++;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void updateProfile(String avatarUrl, String bio, String githubUrl, boolean emailPublic,
                              String displayName) {
        this.avatarUrl = avatarUrl;
        this.bio = bio;
        this.githubUrl = githubUrl;
        this.emailPublic = emailPublic;
        this.displayName = displayName;
    }

    /**
     * 改用户名。
     *
     * <p>单独一个方法而不是塞进 updateProfile：改名需要额外的唯一性检查，
     * 调用方必须显式决定，不能顺手带着一起改。
     *
     * <p>不动 tokenVersion：JWT 的 sub 是用户 id，改名不影响已签发的令牌。
     */
    public void rename(String newUsername) {
        this.username = normalizeUsername(newUsername);
    }

    /**
     * 用户名归一化：登录查询与注册必须用同一套规则，否则"注册时是小写、登录时没转"
     * 就会出现"账号明明存在却登不上"。
     */
    public static String normalizeUsername(String raw) {
        Objects.requireNonNull(raw, "用户名不能为空");
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空白");
        }
        return normalized;
    }

    private static String normalizeEmail(String raw) {
        Objects.requireNonNull(raw, "邮箱不能为空");
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("邮箱不能为空白");
        }
        return normalized;
    }

    private static String requireHash(String passwordHash) {
        Objects.requireNonNull(passwordHash, "密码哈希不能为空");
        if (passwordHash.isBlank()) {
            throw new IllegalArgumentException("密码哈希不能为空白");
        }
        return passwordHash;
    }
}
