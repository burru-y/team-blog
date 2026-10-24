package com.teamblog.auth;

import com.teamblog.common.RateLimitException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 登录限流的计数逻辑（1.4 验收：第 6 次失败返回 429）。
 *
 * <p>不起 Spring：这是纯内存计数 + 一条时间窗口判断，起容器只会拖慢反馈。
 * 时间用可推进的 {@link MutableClock}，不依赖 sleep（Q-04）。
 */
class LoginAttemptServiceTest {

    private static final String ACCOUNT = "u:admin1";
    private static final String IP = "ip:abc123";

    private final LoginProperties properties = new LoginProperties(5, Duration.ofMinutes(10));
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-24T03:00:00Z"));
    private final LoginAttemptService service = new LoginAttemptService(properties, clock);

    @Test
    void 连续失败五次_第六次被拒并给出RetryAfter() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure(ACCOUNT, IP);
        }

        assertThatThrownBy(() -> service.ensureAllowed(ACCOUNT, IP))
                .isInstanceOf(RateLimitException.class)
                .satisfies(ex -> assertThat(((RateLimitException) ex).retryAfterSeconds())
                        .isBetween(1L, 600L));
    }

    @Test
    void 次数未到_仍然放行() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure(ACCOUNT, IP);
        }

        assertThatCode(() -> service.ensureAllowed(ACCOUNT, IP)).doesNotThrowAnyException();
    }

    @Test
    void 登录成功_计数清零() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure(ACCOUNT, IP);
        }
        service.recordSuccess(ACCOUNT, IP);

        assertThatCode(() -> service.ensureAllowed(ACCOUNT, IP)).doesNotThrowAnyException();
    }

    @Test
    void 窗口过期_重新允许() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure(ACCOUNT, IP);
        }
        assertThatThrownBy(() -> service.ensureAllowed(ACCOUNT, IP)).isInstanceOf(RateLimitException.class);

        clock.advance(Duration.ofMinutes(11));

        assertThatCode(() -> service.ensureAllowed(ACCOUNT, IP)).doesNotThrowAnyException();
    }

    @Test
    void 任一维度命中即拦截_账号没被刷爆但IP被刷爆() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("u:another-account", IP);
        }

        // 换个账号，但同一个 IP —— 仍然要拦
        assertThatThrownBy(() -> service.ensureAllowed("u:third-account", IP))
                .isInstanceOf(RateLimitException.class);
    }

    /** 可手动推进的时钟：让"10 分钟后解封"这件事可以瞬间验证。 */
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
