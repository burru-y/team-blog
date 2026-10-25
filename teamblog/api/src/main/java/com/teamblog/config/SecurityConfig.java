package com.teamblog.config;

import com.teamblog.auth.ErrorResponder;
import com.teamblog.auth.JwtFilter;
import com.teamblog.auth.JwtService;
import com.teamblog.common.ErrorCode;
import com.teamblog.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全过滤链：谁能访问哪个路径，以及 401/403 长什么样。
 *
 * <p>为什么是 STATELESS：认证状态在 token 里，服务端不存 Session。JavaWeb 里
 * {@code session.setAttribute} 那套在这里被完全移除——容器不会给你 HttpSession。
 *
 * <p>为什么默认 {@code anyRequest().authenticated()} 而不是默认放行：漏配一个端点的
 * 后果，会从"忘了加权限"变成"完全公开"。默认拒绝，再逐个开口子。
 *
 * <p>为什么 401 与 403 分开：401 是"你没证明你是谁"（没带 token / token 失效），
 * 403 是"知道你是谁，但你不能做这个"。前端据此决定跳登录页还是提示无权限。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ErrorResponder errorResponder;

    public SecurityConfig(JwtService jwtService, UserRepository userRepository, ErrorResponder errorResponder) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.errorResponder = errorResponder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtFilter jwtFilter = new JwtFilter(jwtService, userRepository, errorResponder);

        http
                .csrf(AbstractHttpConfigurer::disable)         // 无 Cookie 会话，CSRF 不适用
                .formLogin(AbstractHttpConfigurer::disable)    // 不用表单登录页
                .httpBasic(AbstractHttpConfigurer::disable)    // 不用 Basic 认证
                .logout(AbstractHttpConfigurer::disable)       // 登出由业务端点处理（批次 1.3）
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 健康检查与只读监控：容器探针要能匿名访问
                        .requestMatchers("/api/v1/health", "/actuator/health", "/actuator/info").permitAll()
                        // 认证端点：登录时还没有 token，刷新与登出用的是 httpOnly Cookie
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout").permitAll()
                        // 邀请注册：注册人当然还没有账号
                        .requestMatchers("/api/v1/invites/**").permitAll()
                        // 团队公开资料：访客（未登录）也要能浏览成员与个人页（批次 2 的 Gate）
                        .requestMatchers(HttpMethod.GET, "/api/v1/users", "/api/v1/users/**").permitAll()
                        // 成果流与详情：访客可见；发布/编辑/下架需要登录（归属校验在 Service）
                        .requestMatchers(HttpMethod.GET, "/api/v1/projects", "/api/v1/projects/**").permitAll()
                        // 留言：访客可以提交（全站唯一无需登录的写接口，靠蜜罐 + 限流防刷）
                        .requestMatchers(HttpMethod.POST, "/api/v1/feedback").permitAll()
                        // 评论：公开阅读；发表与删除需要登录
                        .requestMatchers(HttpMethod.GET, "/api/v1/comments").permitAll()
                        // 热力图：首页要展示团队活跃度，访客可看
                        .requestMatchers(HttpMethod.GET, "/api/v1/stats/heatmap").permitAll()
                        // 上传的文件：由静态资源处理器/Nginx 提供，访客可见
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        // 公告：访客进站就要看到，公开读
                        .requestMatchers(HttpMethod.GET, "/api/v1/announcements").permitAll()
                        // 帮助资料列表：帮助页给所有访客看
                        .requestMatchers(HttpMethod.GET, "/api/v1/help/files").permitAll()
                        // 学习内容：公开阅读；发布/编辑需要登录
                        .requestMatchers(HttpMethod.GET, "/api/v1/learning-logs", "/api/v1/learning-logs/**")
                        .permitAll()
                        // 管理员域：URL 级规则，写接口的统一入口（S-01 的第一道）
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, cause) ->
                                errorResponder.write(response, ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((request, response, cause) ->
                                errorResponder.write(response, ErrorCode.FORBIDDEN)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
