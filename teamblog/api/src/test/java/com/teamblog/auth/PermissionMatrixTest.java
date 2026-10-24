package com.teamblog.auth;

import com.teamblog.user.Role;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * 权限矩阵骨架。现在全部禁用，等批次 1.6 填实现。
 *
 * <p>为什么先建空壳再写接口：Q-05 要求"每个权限敏感接口必须有 403 用例"，而习惯只能
 * 从第一天养成。以后每加一个写接口，第一件事是在 {@link #matrix()} 里加一行——加不上
 * 就说明这个接口的权限设计还没想清楚。
 *
 * <p>三列含义：谁能访问（角色 {@code null} = 未登录）、打哪个端点、期望状态码。
 */
@Disabled("批次 1.6 启用：等 JwtFilter 与 /api/v1/auth/login 就绪")
class PermissionMatrixTest {

    record Case(String method, String path, Role role, int expectedStatus) {
    }

    static Stream<Case> matrix() {
        return Stream.of(
                // 公开端点：未登录也能访问
                new Case("GET", "/api/v1/health", null, 200),

                // 登录态端点：未登录 401，成员 200
                new Case("GET", "/api/v1/me", null, 401),
                new Case("GET", "/api/v1/me", Role.MEMBER, 200),

                // 管理员端点：成员 403，管理员 200
                new Case("GET", "/api/v1/admin/users", null, 401),
                new Case("GET", "/api/v1/admin/users", Role.MEMBER, 403),
                new Case("GET", "/api/v1/admin/users", Role.ADMIN, 200)
        );
    }

    @ParameterizedTest(name = "[{index}] {0} {1} as {2} => {3}")
    @MethodSource("matrix")
    void 权限矩阵(Case testCase) {
        throw new UnsupportedOperationException("批次 1.6 实现：MockMvc 带 token 打端点并断言状态码");
    }
}
