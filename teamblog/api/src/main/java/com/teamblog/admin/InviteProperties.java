package com.teamblog.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 邀请配置。
 *
 * <p>ttl 为什么是 7 天：太短，发出去对方出差回来就过期了；太长，链接在聊天记录里
 * 躺半年仍然有效。7 天是够用且不至于长期暴露的折中。
 *
 * <p>{@code allowAdminRole} 默认 false：邀请 ADMIN 的链接一旦发错群，对方直接拿到管理权限。
 * 需要时在配置里显式打开，而不是默认允许。
 *
 * <p>{@code hourlyLimit} 按管理员限制签发频率，防止账号被盗后批量造管理员。
 */
@Validated
@ConfigurationProperties(prefix = "invite")
public record InviteProperties(
        Duration ttl,
        Boolean allowAdminRole,
        Integer hourlyLimit
) {
    public InviteProperties {
        if (ttl == null) {
            ttl = Duration.ofDays(7);
        }
        if (allowAdminRole == null) {
            allowAdminRole = Boolean.FALSE;
        }
        if (hourlyLimit == null || hourlyLimit <= 0) {
            hourlyLimit = 20;
        }
    }
}