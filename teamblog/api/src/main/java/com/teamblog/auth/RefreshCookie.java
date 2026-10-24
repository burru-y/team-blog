package com.teamblog.auth;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * refresh token 的 Cookie 策略集中在这里。
 *
 * <p>四个属性缺一不可：
 * <ul>
 *   <li>{@code httpOnly}：JS 读不到，XSS 偷不走（T-19）</li>
 *   <li>{@code secure}：只在 HTTPS 上发送，防止明文链路上被截获；本地 http 开发要关</li>
 *   <li>{@code sameSite=Lax}：跨站请求不带，挡住大部分 CSRF</li>
 *   <li>{@code path=/api/v1/auth}：只发给认证端点，其它接口的请求不受影响</li>
 * </ul>
 *
 * <p>为什么 Path 不是更窄的 {@code /api/v1/auth/refresh}：登出也要读这张票，Path 太窄
 * 会导致登出时浏览器不发 Cookie，用户点"退出"却退不掉。
 */
@Component
public class RefreshCookie {

    public static final String NAME = "rt";
    private static final String PATH = "/api/v1/auth";

    private final JwtProperties properties;

    public RefreshCookie(JwtProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie write(String rawToken) {
        return ResponseCookie.from(NAME, rawToken)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path(PATH)
                .maxAge(properties.refreshTtl())
                .build();
    }

    /** 登出：用同名同 Path 的空 Cookie 覆盖，maxAge=0 让浏览器立刻删除。 */
    public ResponseCookie clear() {
        return ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path(PATH)
                .maxAge(Duration.ZERO)
                .build();
    }
}
