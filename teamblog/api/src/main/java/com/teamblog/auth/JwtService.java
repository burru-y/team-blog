package com.teamblog.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.teamblog.user.Role;
import com.teamblog.user.User;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * access token 的签发与校验。
 *
 * <p>为什么是 HS256（对称）而不是 RS256（非对称）：只有一个服务签发、也只有它校验，
 * 对称密钥足够；RS256 要管一对公私钥，收益是"别的服务能验签但不能签发"，我们暂时没有
 * 第二个服务需要验签（AI 服务走的是 HMAC 内网头）。
 *
 * <p>为什么不把用户名放进 token：用户名可以改，放进去就要处理"改名后旧 token 里的
 * 名字是错的"。只放不可变、且校验时一定会查库确认的东西：用户 id、角色、版本号。
 */
@Service
public class JwtService {

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.algorithm = Algorithm.HMAC256(properties.secret());
        // issuer 参与校验：拿着别处签发的同密钥 token 也进不来
        this.verifier = JWT.require(algorithm).withIssuer(properties.issuer()).build();
    }

    /** 签发：签名内容见 {@link AccessTokenClaims}，有效期取配置（默认 30 分钟）。 */
    public String issueAccessToken(User user) {
        Instant now = clock.instant();
        return JWT.create()
                .withIssuer(properties.issuer())
                .withSubject(user.getId().toString())
                .withClaim("role", user.getRole().name())
                .withClaim("tv", user.getTokenVersion())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plus(properties.accessTtl())))
                .sign(algorithm);
    }

    /**
     * 校验签名、issuer 与过期时间，返回业务需要的那几个字段。
     *
     * @throws JWTVerificationException 签名不对、过期、issuer 不符、sub 不是合法 UUID
     */
    public AccessTokenClaims verify(String token) {
        DecodedJWT decoded = verifier.verify(token);
        try {
            Integer tokenVersion = decoded.getClaim("tv").asInt();
            if (tokenVersion == null) {
                throw new JWTVerificationException("token 缺少 tv");
            }
            return new AccessTokenClaims(
                    UUID.fromString(decoded.getSubject()),
                    Role.valueOf(decoded.getClaim("role").asString()),
                    tokenVersion);
        } catch (IllegalArgumentException | NullPointerException ex) {
            // 结构不对也要归成"验签失败"，让过滤器只有一个 catch 分支
            throw new JWTVerificationException("token 载荷不合法", ex);
        }
    }

    /**
     * token 里的业务载荷。
     *
     * <p>{@code tokenVersion} 必须与数据库当前值比对——token 里的是签发那一刻的快照，
     * 查库是为了发现"快照已经过期"（改密码、改角色、被移除都会让它过期）。
     */
    public record AccessTokenClaims(UUID userId, Role role, int tokenVersion) {
    }
}
