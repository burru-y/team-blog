package com.teamblog.admin;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 邀请配置。
 *
 * <p>为什么有效期是 7 天：太短，HR 发出去对方出差回来就过期了；太长，链接在聊天记录里
 * 躺半年仍然有效。7 天是"够用且不至于长期暴露"的折中，且可配置。
 */
@Validated
@ConfigurationProperties(prefix = "invite")
public record InviteProperties(@NotNull Duration ttl) {
}
