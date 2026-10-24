package com.teamblog.auth;

import com.teamblog.common.ErrorCode;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 每个请求解析一次 Bearer token，把身份放进 SecurityContext。
 *
 * <p>为什么校验要查库，而不是只验签名：JWT 是无状态的，签出去就收不回来。改密码、
 * 改角色、移除用户之后，旧 token 在过期前依然"签名有效"。查库比对
 * {@code token_version} 才能让这些操作立即生效（见 {@link JwtService.AccessTokenClaims}）。
 *
 * <p>为什么这个类不加 {@code @Component}：Spring Boot 会把所有 Filter 类型的 Bean 自动
 * 注册进 Servlet 容器，那样它会在安全过滤链**之外**再跑一遍。这里由 SecurityConfig
 * 手动 new 出来、只挂进安全链，执行顺序才可控。
 */
public class JwtFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ErrorResponder errorResponder;

    public JwtFilter(JwtService jwtService, UserRepository userRepository, ErrorResponder errorResponder) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.errorResponder = errorResponder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            // 没有凭证不是错误：公开端点要能匿名访问，需要登录的端点交给
            // AuthenticationEntryPoint 统一返回 401
            chain.doFilter(request, response);
            return;
        }

        JwtService.AccessTokenClaims claims;
        try {
            claims = jwtService.verify(header.substring(BEARER_PREFIX.length()));
        } catch (RuntimeException ex) {
            // 签名不对、过期、issuer 不符、载荷畸形，对外统一是"未登录"
            errorResponder.write(response, ErrorCode.UNAUTHENTICATED);
            return;
        }

        User user = userRepository.findById(claims.userId())
                .filter(candidate -> !candidate.isDeleted())
                .orElse(null);
        if (user == null) {
            errorResponder.write(response, ErrorCode.UNAUTHENTICATED);
            return;
        }

        if (user.getTokenVersion() != claims.tokenVersion()) {
            // 密码/角色变了或被移除，这个 token 必须立刻失效（S-02）
            errorResponder.write(response, ErrorCode.SESSION_REVOKED);
            return;
        }

        AuthPrincipal principal = new AuthPrincipal(user.getId(), user.getUsername(), user.getRole());
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            // 线程池会复用线程，不清理就会把 A 的身份带给下一个请求
            SecurityContextHolder.clearContext();
        }
    }
}
