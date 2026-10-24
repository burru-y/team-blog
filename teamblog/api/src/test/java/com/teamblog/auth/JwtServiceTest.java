package com.teamblog.auth;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.teamblog.user.Role;
import com.teamblog.user.User;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 签发与校验的单元测试（1.2 验收：有效 token 通过、篡改后失败）。
 *
 * <p>不启动 Spring：签名是纯函数，起容器只会把失败原因埋进 bean 装配日志。
 */
class JwtServiceTest {

    private static final String SECRET = "test-only-secret-key-with-at-least-32-bytes";
    private static final String ISSUER = "teamblog";

    private final JwtProperties properties =
            new JwtProperties(SECRET, Duration.ofMinutes(30), Duration.ofDays(7), ISSUER, false);
    private final JwtService jwtService = new JwtService(properties, Clock.systemUTC());

    @Test
    void 签发后校验_载荷字段与用户一致() {
        User user = aUser();
        user.changeRole(Role.ADMIN);   // tokenVersion 变成 1，顺便验证它被写进 token

        String token = jwtService.issueAccessToken(user);
        JwtService.AccessTokenClaims claims = jwtService.verify(token);

        assertThat(claims.userId()).isEqualTo(user.getId());
        assertThat(claims.role()).isEqualTo(Role.ADMIN);
        assertThat(claims.tokenVersion()).isEqualTo(1);
    }

    @Test
    void 篡改任意一位_签名校验失败() {
        String token = jwtService.issueAccessToken(aUser());
        String tampered = flipOneCharInPayload(token);

        assertThatThrownBy(() -> jwtService.verify(tampered))
                .isInstanceOf(JWTVerificationException.class);
    }

    @Test
    void 换一个密钥签发_校验失败() {
        JwtService otherIssuer = new JwtService(
                new JwtProperties("another-secret-key-with-at-least-32-bytes",
                        Duration.ofMinutes(30), Duration.ofDays(7), ISSUER, false),
                Clock.systemUTC());
        String foreignToken = otherIssuer.issueAccessToken(aUser());

        assertThatThrownBy(() -> jwtService.verify(foreignToken))
                .isInstanceOf(JWTVerificationException.class);
    }

    @Test
    void 已过期_校验失败() {
        // 用"31 分钟前的时钟"签发，token 在签发时就已过期——不用 sleep 也能测过期（Q-04）
        Instant past = Instant.now().minus(Duration.ofMinutes(31));
        JwtService pastIssuer = new JwtService(properties, Clock.fixed(past, ZoneOffset.UTC));
        String expiredToken = pastIssuer.issueAccessToken(aUser());

        assertThatThrownBy(() -> jwtService.verify(expiredToken))
                .isInstanceOf(JWTVerificationException.class);
    }

    @Test
    void 别的issuer签发_校验失败() {
        JwtService foreignIssuer = new JwtService(
                new JwtProperties(SECRET, Duration.ofMinutes(30), Duration.ofDays(7), "not-teamblog", false),
                Clock.systemUTC());
        String foreignToken = foreignIssuer.issueAccessToken(aUser());

        assertThatThrownBy(() -> jwtService.verify(foreignToken))
                .isInstanceOf(JWTVerificationException.class);
    }

    private static User aUser() {
        return User.create("member1", "member1@example.com", "$2a$12$not-a-real-hash", Role.MEMBER);
    }

    /** 改 payload 里的一个字符，签名自然对不上。 */
    private static String flipOneCharInPayload(String token) {
        String[] parts = token.split("\\.");
        char[] payload = parts[1].toCharArray();
        payload[0] = payload[0] == 'e' ? 'f' : 'e';
        return parts[0] + "." + new String(payload) + "." + parts[2];
    }
}
