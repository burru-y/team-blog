package com.teamblog.auth;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 登录限流参数（J-07：配置项集中，不散落 @Value）。
 *
 * <p>为什么阈值放配置而不是常量：限流的松紧跟部署环境有关。上线后如果发现正常用户
 * 被误伤，改配置重启即可，不用改代码发版。
 *
 * <p>默认 5 次 / 10 分钟：正常用户连续输错 5 次密码，基本可以确定不是手滑；
 * 而 10 分钟窗口足够打断在线爆破，又不会让真正忘记密码的人等太久。
 */
@Validated
@ConfigurationProperties(prefix = "auth.login")
public record LoginProperties(
        @Min(1) int maxFailures,
        @NotNull Duration window
) {
}
