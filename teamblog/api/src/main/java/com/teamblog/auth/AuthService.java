package com.teamblog.auth;

import com.teamblog.auth.dto.LoginRequest;
import com.teamblog.common.BusinessException;
import com.teamblog.common.ErrorCode;
import com.teamblog.common.HashUtils;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * 登录 / 刷新 / 登出的业务编排。
 *
 * <p>为什么和 {@link RefreshTokenService} 分两个类：这个类回答"谁能登录、给谁发什么"，
 * 那个类回答"票怎么轮换、怎么判定重放"。混在一起，测试要么起数据库测密码校验，
 * 要么 mock 掉大半。
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final JwtProperties properties;
    private final Clock clock;

    /**
     * 一个永远匹配不上的 BCrypt 哈希，用于"用户不存在"时也跑一次比对。
     *
     * <p>不这么做的话：不存在的用户 5ms 返回、存在的用户 250ms 返回，攻击者不用登录
     * 就能按响应时间把有效用户名筛出来（计时侧信道）。
     */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       LoginAttemptService loginAttemptService,
                       JwtProperties properties,
                       Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptService = loginAttemptService;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    /**
     * 用户名密码登录，返回 access + refresh 一对凭据。
     *
     * <p>四件事按顺序做，顺序不能换：
     * <ol>
     *   <li>限流检查 —— 命中就不必再花 250ms 跑 BCrypt</li>
     *   <li>等时比对 —— 用户不存在也跑一次 BCrypt，堵住计时侧信道</li>
     *   <li>失败统一文案 —— "用户不存在"与"密码错误"同一个 401</li>
     *   <li>成功清零计数并记录登录时间</li>
     * </ol>
     */
    @Transactional
    public IssuedTokens login(LoginRequest request, String clientIp) {
        String username = User.normalizeUsername(request.username());
        String accountKey = "u:" + username;
        String ipKey = "ip:" + HashUtils.sha256Hex(clientIp == null ? "unknown" : clientIp);

        loginAttemptService.ensureAllowed(accountKey, ipKey);

        User user = userRepository.findActiveByUsername(username).orElse(null);
        String hashToCheck = user == null ? dummyPasswordHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        if (user == null || !passwordMatches) {
            loginAttemptService.recordFailure(accountKey, ipKey);
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "用户名或密码错误");
        }

        loginAttemptService.recordSuccess(accountKey, ipKey);
        user.markLoggedIn(OffsetDateTime.now(clock));
        return issueTokens(user);
    }

    /**
     * 用 refresh token 换一对新凭据。
     *
     * <p>顺序很重要：先轮换（可能因重放而撤销全部会话、抛异常），再签 access token。
     * 反过来会在重放时先发出去一个有效的 access token。
     */
    @Transactional
    public IssuedTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "缺少刷新凭据");
        }
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(rawRefreshToken);

        User user = userRepository.findById(rotation.userId())
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));

        String accessToken = jwtService.issueAccessToken(user);
        return new IssuedTokens(accessToken,
                OffsetDateTime.now(clock).plus(properties.accessTtl()),
                rotation.rawToken(),
                rotation.expiresAt());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private IssuedTokens issueTokens(User user) {
        String accessToken = jwtService.issueAccessToken(user);
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user.getId());
        return new IssuedTokens(accessToken,
                OffsetDateTime.now(clock).plus(properties.accessTtl()),
                refreshToken.rawToken(),
                refreshToken.expiresAt());
    }

    /** access 放响应体，refresh 放 httpOnly Cookie——两者必须成对处理。 */
    public record IssuedTokens(String accessToken,
                               OffsetDateTime accessExpiresAt,
                               String refreshToken,
                               OffsetDateTime refreshExpiresAt) {
    }
}
