package com.teamblog.admin;

import com.teamblog.common.BusinessException;
import com.teamblog.common.ErrorCode;
import com.teamblog.common.HashUtils;
import com.teamblog.user.Role;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 邀请的签发与兑换。
 *
 * <p>一次性怎么保证：兑换时先做一条**条件更新**（`where used_at is null`），
 * 更新行数为 0 就说明被别人抢先用了。数据库保证只有一个事务能更新成功，
 * 所以哪怕两个请求同时到达，也只会有一个人注册成功（见 {@link AdminInviteRepository}）。
 */
@Service
public class AdminInviteService {

    private static final int TOKEN_BYTES = 32;

    private final AdminInviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final InviteProperties properties;
    private final InviteRateLimiter rateLimiter;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminInviteService(AdminInviteRepository inviteRepository,
                              UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              InviteProperties properties,
                              InviteRateLimiter rateLimiter,
                              Clock clock) {
        this.inviteRepository = inviteRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /**
     * 生成邀请。
     *
     * @return 明文 token —— **只在这里返回一次**，库里存的是哈希
     */
    @Transactional
    public IssuedInvite create(UUID createdBy, String email, Role role) {
        Long retryAfter = rateLimiter.checkAndRecord("admin:" + createdBy);
        if (retryAfter != null) {
            throw new BusinessException(ErrorCode.RATE_LIMITED,
                    "签发过于频繁，请 " + retryAfter + " 秒后再试");
        }

        if (role == Role.ADMIN && !Boolean.TRUE.equals(properties.allowAdminRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前不允许签发管理员邀请");
        }

        String rawToken = newRawToken();
        AdminInvite invite = AdminInvite.create(
                createdBy, HashUtils.sha256Hex(rawToken),
                normalizeEmail(email), role,
                OffsetDateTime.now(clock), properties.ttl());
        inviteRepository.save(invite);
        return new IssuedInvite(invite.getId(), rawToken, invite.getEmail(), invite.getRole(),
                invite.getExpiresAt());
    }

    /** 邀请列表：管理员查看已签发的邀请及其状态。 */
    @Transactional(readOnly = true)
    public List<AdminInvite> list() {
        return inviteRepository.findTop100ByOrderByCreatedAtDesc();
    }

    /**
     * 撤销邀请。
     *
     * <p>已使用的邀请不能撤销：账号已经建出来了，"撤回邀请"没有任何意义，
     * 需要的是删除账号，那是另一个动作。
     */
    @Transactional
    public AdminInvite revoke(UUID inviteId) {
        AdminInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "邀请不存在"));
        if (invite.isUsed()) {
            throw new BusinessException(ErrorCode.CONFLICT, "已使用的邀请不能撤销");
        }
        invite.revoke(OffsetDateTime.now(clock));
        return invite;
    }

    /** 注册页先查一次：让用户知道"这个链接是给谁的、还有效吗"。 */
    @Transactional(readOnly = true)
    public AdminInvite requireUsable(String rawToken) {
        return loadUsable(rawToken);
    }

    /**
     * 兑换邀请并创建账号。
     *
     * <p>顺序是刻意的：**先原子占用，再建用户**。如果先建用户再占用，两个并发请求会都
     * 建出账号；如果占用失败，事务回滚，用户记录也不会留下。
     */
    @Transactional
    public User accept(String rawToken, String usernameRaw, String passwordRaw, String emailRaw) {
        AdminInvite invite = loadUsable(rawToken);
        OffsetDateTime now = OffsetDateTime.now(clock);

        int claimed = inviteRepository.markUsedIfUnused(invite.getId(), now);
        if (claimed == 0) {
            // 乐观锁失败：同一瞬间有另一个人用了这张邀请
            throw new BusinessException(ErrorCode.INVITE_INVALID, "邀请已被使用");
        }

        String email = invite.getEmail() != null ? invite.getEmail() : normalizeEmail(emailRaw);
        if (email == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "这张邀请没有指定邮箱，请填写");
        }
        String username = User.normalizeUsername(usernameRaw);
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已被占用");
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.CONFLICT, "邮箱已被占用");
        }

        User user = User.create(username, email,
                passwordEncoder.encode(passwordRaw), invite.getRole());
        return userRepository.save(user);
    }

    private AdminInvite loadUsable(String rawToken) {
        AdminInvite invite = inviteRepository.findByTokenHash(HashUtils.sha256Hex(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_INVALID, "邀请链接无效或已失效"));
        if (invite.isUsed()) {
            throw new BusinessException(ErrorCode.INVITE_INVALID, "邀请已被使用");
        }
        if (invite.isRevoked()) {
            throw new BusinessException(ErrorCode.INVITE_INVALID, "邀请已被撤销");
        }
        if (invite.isExpired(OffsetDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.INVITE_INVALID, "邀请已过期");
        }
        return invite;
    }

    private String newRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** 明文 token 只应出现在返回给管理员的响应里，不要写日志（L-05）。 */
    public record IssuedInvite(UUID id, String rawToken, String email, Role role,
                               OffsetDateTime expiresAt) {
    }
}
