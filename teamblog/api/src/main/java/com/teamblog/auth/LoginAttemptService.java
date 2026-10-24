package com.teamblog.auth;

import com.teamblog.common.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败计数与限流。
 *
 * <p>为什么按"账号 + IP"两个维度同时记：只按账号，攻击者换账号就能绕过；只按 IP，
 * 一个 NAT 出口后面的正常用户会被邻居连累。同时限制，任一维度命中就拦。
 *
 * <p>为什么放内存而不是 Redis：2C4G 单机部署，为一张计数表引 Redis 不值得（禁止清单
 * 第 13 条）。代价是**多实例不共享**——将来真要扩容，这里的接口就是替换点。
 *
 * <p>为什么窗口是"固定窗口"而不是漏桶/令牌桶：登录失败的分布是突发的（爆破），
 * 固定窗口足够；滑动窗口要在每次请求存一串时间戳，内存开销大十倍。
 */
@Slf4j
@Component
public class LoginAttemptService {

    /** 防止被随机账号刷爆内存：超过这个规模就强制清理一次。 */
    private static final int MAX_TRACKED_ENTRIES = 100_000;

    private final LoginProperties properties;
    private final Clock clock;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(LoginProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 在验证密码**之前**检查：命中限流就不必再花 250ms 跑 BCrypt。
     *
     * @throws RateLimitException 命中任一维度的限流
     */
    public void ensureAllowed(String... keys) {
        Instant now = clock.instant();
        for (String key : keys) {
            Attempt attempt = attempts.get(key);
            if (attempt == null) {
                continue;
            }
            if (isExpired(attempt, now)) {
                attempts.remove(key, attempt);
                continue;
            }
            if (attempt.failures() >= properties.maxFailures()) {
                // 剩余秒数（至少 1）：不要把 +1 的余量加上去，否则会超过窗口长度
                long retryAfter = Math.max(1,
                        Duration.between(now, attempt.windowStart().plus(properties.window())).toSeconds());
                // 文案与"密码错误"完全一致：不告诉攻击者"这个账号被锁了"——
                // 那本身就是"账号存在"的信号（见 04 文档 1.4 验收）
                throw new RateLimitException(retryAfter, "用户名或密码错误");
            }
        }
    }

    public void recordFailure(String... keys) {
        if (attempts.size() > MAX_TRACKED_ENTRIES) {
            purgeExpired();
        }
        Instant now = clock.instant();
        for (String key : keys) {
            attempts.compute(key, (ignored, previous) -> {
                if (previous == null || isExpired(previous, now)) {
                    return new Attempt(now, 1);
                }
                return new Attempt(previous.windowStart(), previous.failures() + 1);
            });
        }
    }

    /** 登录成功即清零：避免"白天输错 4 次，晚上正常登录还要提心吊胆"。 */
    public void recordSuccess(String... keys) {
        for (String key : keys) {
            attempts.remove(key);
        }
    }

    /** 定期清掉过期条目，否则每个失败过的键都会永久占一份内存。 */
    @Scheduled(fixedDelay = 600_000)
    public void purgeExpired() {
        Instant now = clock.instant();
        int before = attempts.size();
        attempts.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
        int removed = before - attempts.size();
        if (removed > 0) {
            log.debug("清理过期登录失败记录 {} 条，剩余 {}", removed, attempts.size());
        }
    }

    private boolean isExpired(Attempt attempt, Instant now) {
        return !attempt.windowStart().plus(properties.window()).isAfter(now);
    }

    private record Attempt(Instant windowStart, int failures) {
    }
}
