package com.teamblog.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 首启管理员配置。
 *
 * <p>password 默认空串：**没有默认密码**。默认值如果是 "admin123"，
 * 那就等于每个忘记配置的环境都有一个公开后门。
 */
@ConfigurationProperties(prefix = "admin.bootstrap")
public record AdminBootstrapProperties(String username, String password) {

    public AdminBootstrapProperties {
        if (username == null || username.isBlank()) {
            username = "admin";
        }
        if (password == null) {
            password = "";
        }
    }
}