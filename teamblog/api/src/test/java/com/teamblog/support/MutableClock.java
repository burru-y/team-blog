package com.teamblog.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * 可拨动的时钟（测试专用）。
 *
 * <p>"连续 7 天""跨天""月底"这类规则，如果代码里直接调 {@code LocalDate.now()}，
 * 就只能等到那天再验证。注入一个可拨的 Clock，就能在一个测试里把 30 天走完。
 *
 * <p>时区固定 Asia/Shanghai：业务规则一律按东八区的自然日切分（J-14），
 * 测试也要用同一个时区，否则"UTC 16:30 算次日"这类用例根本没法表达。
 */
public final class MutableClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    /** 从某个上海日期的 09:00 开始，方便构造"某天打卡"的场景。 */
    public static MutableClock atStartOfDay(java.time.LocalDate date) {
        return new MutableClock(date.atTime(9, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant(),
                ZoneId.of("Asia/Shanghai"));
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    public void plusDays(long days) {
        this.instant = this.instant.plus(Duration.ofDays(days));
    }

    public void plusHours(long hours) {
        this.instant = this.instant.plus(Duration.ofHours(hours));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
