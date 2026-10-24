package com.teamblog.auth;

import com.teamblog.common.BusinessException;
import com.teamblog.common.ErrorCode;
import com.teamblog.user.Role;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * refresh token 的轮换、过期、撤销、清理（1.3 单测）。
 *
 * <p>重放检测不在这里测：它调用 {@link SessionRevoker}，而后者是 REQUIRES_NEW，
 * 会提交到测试事务之外，无法靠 @Transactional 回滚。放在
 * {@code AuthIntegrationTest} 里，用显式清理来保证可重复（Q-09）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RefreshTokenServiceTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 轮换_旧票作废且指向新票_新票可继续用() {
        User user = persistUser();
        RefreshTokenService.IssuedRefreshToken first = refreshTokenService.issue(user.getId());

        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(first.rawToken());

        assertThat(rotation.userId()).isEqualTo(user.getId());
        assertThat(rotation.rawToken()).isNotEqualTo(first.rawToken());

        RefreshToken oldToken = refreshTokenRepository.findByTokenHash(RefreshToken.hash(first.rawToken())).orElseThrow();
        assertThat(oldToken.isRevoked()).isTrue();
        assertThat(oldToken.getReplacedById()).isNotNull();

        // 新票还能继续换
        assertThat(refreshTokenService.rotate(rotation.rawToken()).userId()).isEqualTo(user.getId());
    }

    @Test
    void 已过期_拒绝并提示重新登录() {
        User user = persistUser();
        String rawToken = "expired-token-for-test";
        refreshTokenRepository.save(RefreshToken.issue(
                user.getId(), RefreshToken.hash(rawToken),
                OffsetDateTime.now().minus(Duration.ofDays(10)), Duration.ofHours(1)));

        assertThatThrownBy(() -> refreshTokenService.rotate(rawToken))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void 未知票据_拒绝() {
        assertThatThrownBy(() -> refreshTokenService.rotate("no-such-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void 登出_只作废当前票_不影响同一用户其它设备() {
        User user = persistUser();
        RefreshTokenService.IssuedRefreshToken deviceA = refreshTokenService.issue(user.getId());
        RefreshTokenService.IssuedRefreshToken deviceB = refreshTokenService.issue(user.getId());

        refreshTokenService.revoke(deviceA.rawToken());

        assertThat(refreshTokenRepository.findByTokenHash(RefreshToken.hash(deviceA.rawToken()))
                .orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findByTokenHash(RefreshToken.hash(deviceB.rawToken()))
                .orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void 清理_删除过期超过保留期的记录() {
        User user = persistUser();
        refreshTokenRepository.save(RefreshToken.issue(
                user.getId(), RefreshToken.hash("ancient-token"),
                OffsetDateTime.now().minus(Duration.ofDays(60)), Duration.ofHours(1)));

        int deleted = refreshTokenService.purgeExpired(Duration.ofDays(30));

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash(RefreshToken.hash("ancient-token"))).isEmpty();
    }

    private User persistUser() {
        String name = "rt" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return userRepository.saveAndFlush(User.create(
                name, name + "@example.com", "$2a$12$not-a-real-hash", Role.MEMBER));
    }
}
