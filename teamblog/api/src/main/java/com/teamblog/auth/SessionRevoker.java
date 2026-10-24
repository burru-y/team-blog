package com.teamblog.auth;

import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 处置"已确认泄漏"的会话：撤销该用户全部 refresh token，并让 access token 立即失效。
 *
 * <p>为什么单独一个类、且 {@code REQUIRES_NEW}：调用方（{@link RefreshTokenService}）
 * 检测到重放后要**抛异常**返回 401。如果撤销动作和它在同一个事务里，异常会把事务标记
 * 回滚——撤销一笔都不落库，攻击者的会话安然无恙。用新事务先提交，再抛异常，才能既
 * "踢人"又"返回正确状态码"。
 *
 * <p>这也是规范里"@Transactional 自调用失效"的真实案例：放在同一个类里，即使方法上写了
 * REQUIRES_NEW，自调用也不经过代理，照样不生效。
 */
@Slf4j
@Service
public class SessionRevoker {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public SessionRevoker(UserRepository userRepository,
                          RefreshTokenRepository refreshTokenRepository,
                          Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAll(UUID userId, String reason) {
        OffsetDateTime now = OffsetDateTime.now(clock);

        List<RefreshToken> active = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId);
        active.forEach(token -> token.revoke(now));
        refreshTokenRepository.saveAll(active);

        userRepository.findById(userId)
                .filter(user -> !user.isDeleted())
                .ifPresent(User::invalidateSessions);

        // 只记事件与数量，不记 token 本身（L-05）
        log.warn("撤销用户全部会话 userId={} 撤销 refresh token {} 张，原因={}",
                userId, active.size(), reason);
    }
}
