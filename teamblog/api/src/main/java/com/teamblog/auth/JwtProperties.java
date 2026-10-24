package com.teamblog.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 认证配置（J-07：配置项一律 @ConfigurationProperties，不散落 @Value）。
 *
 * <p>secret 没有默认值：缺了就启动失败。给默认值的后果是——本地跑得好好的，上线时
 * 忘了配环境变量，所有人共用仓库里那个"开发用密钥"，任何人都能伪造管理员 token（S-07）。
 *
 * <p>至少 32 字节：HMAC-SHA256 的密钥长度低于 256 位时，签名强度由密钥长度决定而不是
 * 算法名决定，8 位密码当密钥等于没签名。
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotNull Duration accessTtl,
        @NotNull Duration refreshTtl,
        @NotBlank String issuer,
        boolean cookieSecure
) {

    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret != null && secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "jwt.secret 至少 %d 字节（当前 %d），HS256 的密钥强度由长度决定"
                            .formatted(MIN_SECRET_BYTES, secret.getBytes(StandardCharsets.UTF_8).length));
        }
    }
}
