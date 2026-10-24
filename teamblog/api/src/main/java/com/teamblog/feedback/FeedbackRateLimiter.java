package com.teamblog.feedback;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 留言限流：按 IP 做固定窗口。
 *
 * <p>为什么留言必须限流：它是全站唯一"不需要登录"的写接口。没有限流，一个脚本
 * 一分钟就能把数据库灌满，而管理员还以为是有人真的在反馈。
 *
 * <p>为什么用内存而不是 Redis：单实例部署，重启后计数清零的代价只是"多放几条留言"。
 * 引入 Redis 会多一个必须运维的组件——等真的要多实例时再换。
 *
 * <p>为什么要有 {@code MAX_TRACKED_ENTRIES}：内存 Map 的键来自外部输入（IP），
 * 无上限就意味着可以被撑爆。超过上限直接清空，代价是短暂放宽限流，但不会 OOM。
 */
@org.springframework.stereotype.Component
public class FeedbackRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);
    private static final int LIMIT = 3;
    private static final int MAX_TRACKED_ENTRIES = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public FeedbackRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * 记录一次请求。
     *
     * @return 允许则返回 null；被限流则返回"还需要等多少秒"
     */
    public Long checkAndRecord(String key) {
        Instant now = Instant.now(clock);

        if (windows.size() > MAX_TRACKED_ENTRIES) {
            windows.clear();
        }

        Window window = windows.compute(key, (k, current) -> {
            if (current == null || current.startedAt.plus(WINDOW).isBefore(now)) {
                return new Window(now, 1);
            }
            return new Window(current.startedAt, current.count + 1);
        });

        if (window.count > LIMIT) {
            long retryAfter = Duration.between(now, window.startedAt.plus(WINDOW)).toSeconds();
            return Math.max(retryAfter, 1);
        }
        return null;
    }

    private record Window(Instant startedAt, int count) {
    }
}
