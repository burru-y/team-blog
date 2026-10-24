package com.teamblog.admin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 邀请签发限流：按管理员做固定窗口。
 *
 * <p>为什么这条必须有：签发接口能造出 ADMIN 角色的邀请。管理员账号一旦被盗，
 * 攻击者可以无限签发邀请，等于无限造管理员。限流把损失从"无限"压到"每小时 N 张"。
 *
 * <p>与登录限流同理用内存实现：单实例部署，重启清零的代价可以接受。
 */
@org.springframework.stereotype.Component
public class InviteRateLimiter {

    private static final int MAX_TRACKED_ENTRIES = 1_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final InviteProperties properties;

    public InviteRateLimiter(Clock clock, InviteProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * 记录一次签发请求。
     *
     * @return 允许则返回 null；被限流则返回还需等待的秒数
     */
    public Long checkAndRecord(String key) {
        Instant now = Instant.now(clock);

        if (windows.size() > MAX_TRACKED_ENTRIES) {
            windows.clear();
        }

        Window window = windows.compute(key, (k, current) -> {
            if (current == null || current.startedAt.plus(Duration.ofHours(1)).isBefore(now)) {
                return new Window(now);
            }
            current.hit();
            return current;
        });

        if (window.count() > properties.hourlyLimit()) {
            long seconds = Duration.between(now, window.startedAt.plus(Duration.ofHours(1))).toSeconds();
            return Math.max(seconds, 1);
        }
        return null;
    }

    private static final class Window {
        private final Instant startedAt;
        private final AtomicInteger count = new AtomicInteger();

        Window(Instant startedAt) {
            this.startedAt = startedAt;
            this.count.set(1);
        }

        int count() {
            return count.get();
        }

        void hit() {
            count.incrementAndGet();
        }
    }
}
