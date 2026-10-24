package com.teamblog.auth;

import com.teamblog.auth.dto.LoginRequest;
import com.teamblog.auth.dto.TokenResponse;
import com.teamblog.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证端点：登录、刷新、登出。
 *
 * <p>为什么用 {@code ResponseEntity} 携带 Set-Cookie，而不是注入
 * {@code HttpServletResponse}：Controller 方法参数保持"只收业务数据"（J-32），
 * Cookie 这类传输细节通过响应头表达，测试里也更好断言。
 *
 * <p>三个端点都不要求登录态（登录时当然还没有 token；刷新时用的是 Cookie），
 * 但都不在 {@code permitAll} 之外——它们由安全配置显式放行，见 SecurityConfig。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookie refreshCookie;

    public AuthController(AuthService authService, RefreshCookie refreshCookie) {
        this.authService = authService;
        this.refreshCookie = refreshCookie;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest httpRequest) {
        AuthService.IssuedTokens tokens = authService.login(request, clientIp(httpRequest));
        return withRefreshCookie(tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @CookieValue(name = RefreshCookie.NAME, required = false) String rawRefreshToken) {
        AuthService.IssuedTokens tokens = authService.refresh(rawRefreshToken);
        return withRefreshCookie(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = RefreshCookie.NAME, required = false) String rawRefreshToken) {
        authService.logout(rawRefreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString())
                .body(ApiResponse.ok(null));
    }

    private ResponseEntity<ApiResponse<TokenResponse>> withRefreshCookie(AuthService.IssuedTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.write(tokens.refreshToken()).toString())
                .body(ApiResponse.ok(new TokenResponse(tokens.accessToken(), tokens.accessExpiresAt())));
    }

    /**
     * 取客户端 IP 用于登录限流。
     *
     * <p>为什么取 X-Forwarded-For 的**最后一个**：部署在 Nginx 后面时，Nginx 会把真实
     * 来源追加到末尾，而前面几段是客户端自己写的、可随意伪造。取第一个等于让攻击者
     * 自选 IP 绕过限流。
     *
     * <p>本地直连时没有这个头，退化为 remoteAddr。
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
