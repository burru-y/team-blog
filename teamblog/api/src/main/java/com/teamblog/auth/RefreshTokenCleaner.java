package com.teamblog.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 每天清理过期的 refresh token。
 *
 * <p>为什么必须清理：每次登录、每次刷新都插一行，一年下来是几十万行。表大了以后
 * 那个唯一索引的维护成本会拖慢每次刷新，而这恰恰是高频路径。
 *
 * <p>为什么保留 30 天而不是立刻删：已作废的票要留着供"重放检测"识别——一张作废票
 * 再次出现才是泄漏信号。删早了，重放就变成"查不到 → 401"，看起来和普通过期一样，
 * 永远发现不了被盗。
 *
 * <p>放在凌晨 4:30（Asia/Shanghai）：业务低峰，且不跨天，避免和打卡的日切任务抢。
 */
@Slf4j
@Component
public class RefreshTokenCleaner {

    private static final Duration RETENTION = Duration.ofDays(30);

    private final RefreshTokenService refreshTokenService;

    public RefreshTokenCleaner(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Shanghai")
    public void purgeExpiredTokens() {
        try {
            refreshTokenService.purgeExpired(RETENTION);
        } catch (RuntimeException ex) {
            // 定时任务里吞异常可以，但必须打日志：否则每天都在失败却没人知道（L-04）
            log.error("清理 refresh token 失败", ex);
        }
    }
}
