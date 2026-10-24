package com.teamblog.auth;

import com.teamblog.common.BusinessException;
import com.teamblog.common.ErrorCode;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * refresh token 的生命周期：签发、轮换、撤销、过期清理。
 *
 * <p>核心是**轮换**：每次刷新都作废旧票、发新票。这样一张票只能用一次，泄漏窗口从
 * "7 天"缩短到"下一次刷新之前"。
 *
 * <p>以及**重放检测**：作废的票再次出现只有两种可能——攻击者偷到了，或者客户端并发
 * 刷新。两种情况都必须按"已泄漏"处理（撤销全部会话），因为无法区分。宁可让用户重登一次，
 * 也不能放过一个被盗的凭据。
 */
@Slf4j
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionRevoker sessionRevoker;
    private final JwtProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               SessionRevoker sessionRevoker,
                               JwtProperties properties,
                               Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.sessionRevoker = sessionRevoker;
        this.properties = properties;
        this.clock = clock;
    }

    /** 签发新票（登录时）。明文只在这一刻存在，库里落的是哈希。 */
    @Transactional
    public IssuedRefreshToken issue(UUID userId) {
        String rawToken = newRawToken();
        RefreshToken token = RefreshToken.issue(
                userId, RefreshToken.hash(rawToken), OffsetDateTime.now(clock), properties.refreshTtl());
        refreshTokenRepository.save(token);
        return new IssuedRefreshToken(rawToken, token.getExpiresAt());
    }

    /**
     * 轮换：作废旧票、发新票。
     *
     * @throws BusinessException UNAUTHENTICATED（票不存在/已过期）、
     *                           SESSION_REVOKED（检测到重放，已撤销全部会话）
     */
    @Transactional
    public RotationResult rotate(String rawToken) {
        RefreshToken current = refreshTokenRepository.findByTokenHash(RefreshToken.hash(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED, "刷新凭据无效"));

        if (current.isRevoked()) {
            // 两类作废要分开处理：
            //   被轮换替换掉的（replaced_by_id 有值）再出现 = 有人拿着旧票重放 → 撤销全部会话
            //   主动登出作废的（replaced_by_id 为空）再出现 = 客户端重试/浏览器重发 → 只拒绝
            // 不分开的话，用户"点退出 → 前端又自动刷新一次"就会被当成攻击，踢掉所有设备。
            if (current.getReplacedById() != null) {
                sessionRevoker.revokeAll(current.getUserId(), "refresh token 重放");
                throw new BusinessException(ErrorCode.SESSION_REVOKED, "凭据已被使用过，已退出全部设备");
            }
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "登录已失效，请重新登录");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (current.isExpired(now)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "登录已过期，请重新登录");
        }

        String rawNext = newRawToken();
        RefreshToken next = RefreshToken.issue(
                current.getUserId(), RefreshToken.hash(rawNext), now, properties.refreshTtl());
        refreshTokenRepository.save(next);
        current.replaceWith(next, now);

        return new RotationResult(current.getUserId(), rawNext, next.getExpiresAt());
    }

    /** 登出：只作废当前这张票，不影响同一用户其它设备。 */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(RefreshToken.hash(rawToken))
                .ifPresent(token -> token.revoke(OffsetDateTime.now(clock)));
    }

    /** 清理：删掉过期超过 retention 的记录（已作废但未过期的先留着，供重放检测使用）。 */
    @Transactional
    public int purgeExpired(Duration retention) {
        int deleted = refreshTokenRepository.deleteExpiredBefore(OffsetDateTime.now(clock).minus(retention));
        if (deleted > 0) {
            log.info("清理过期 refresh token {} 条", deleted);
        }
        return deleted;
    }

    private String newRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 明文票只在 Cookie 里存在一次；这个方法返回值不要写日志（L-05）。 */
    public record IssuedRefreshToken(String rawToken, OffsetDateTime expiresAt) {
    }

    /** 轮换结果：调用方需要 userId 去签 access token。 */
    public record RotationResult(UUID userId, String rawToken, OffsetDateTime expiresAt) {
    }
}
