package com.teamblog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 时间的唯一来源。
 *
 * <p>为什么必须有它：业务规则里到处是"今天""最近 30 天""连续 7 天"，直接调
 * {@code LocalDate.now()} 的代码没法测跨天、月底、闰年——你只能等到那天再看。
 * 注入 Clock 之后，测试里塞一个固定时刻就能把所有边界跑一遍（J-13）。
 *
 * <p>为什么时区是 Asia/Shanghai 而不是 UTC：打卡、等级、连续奖励都按"北京时间的自然日"
 * 切分。用 UTC 会让 0:00–8:00 打卡的人被算到前一天（J-14）。
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
