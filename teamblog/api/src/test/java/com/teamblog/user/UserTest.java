package com.teamblog.user;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * User 实体的不变量测试（1.1 验收项：密码哈希往返）。
 *
 * <p>不启动 Spring 上下文：这些规则是纯对象行为，起了容器反而把失败原因藏在 bean 装配里。
 */
class UserTest {

    private static final int BCRYPT_STRENGTH = 12;
    private static final String RAW_PASSWORD = "S3cret!pass";

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);

    @Test
    void create_密码哈希_不可逆且能校验原文() {
        User user = User.create("admin1", "admin1@example.com", encoder.encode(RAW_PASSWORD), Role.ADMIN);

        assertThat(user.getPasswordHash())
                .isNotEqualTo(RAW_PASSWORD)
                .startsWith("$2a$12$");
        assertThat(encoder.matches(RAW_PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(encoder.matches("wrong-password", user.getPasswordHash())).isFalse();
    }

    @Test
    void create_用户名与邮箱_统一转小写并去首尾空格() {
        User user = User.create("  Admin1  ", "  Admin1@Example.COM ", encoder.encode(RAW_PASSWORD), Role.ADMIN);

        assertThat(user.getUsername()).isEqualTo("admin1");
        assertThat(user.getEmail()).isEqualTo("admin1@example.com");
    }

    @Test
    void create_初始状态_积分为零且tokenVersion为零() {
        User user = User.create("member1", "member1@example.com", encoder.encode(RAW_PASSWORD), Role.MEMBER);

        assertThat(user.getId()).isNotNull();
        assertThat(user.getRole()).isEqualTo(Role.MEMBER);
        assertThat(user.getTotalPoints()).isZero();
        assertThat(user.getTokenVersion()).isZero();
        assertThat(user.isEmailPublic()).isFalse();
        assertThat(user.isDeleted()).isFalse();
    }

    @Test
    void changePassword_旧密码失效且tokenVersion自增() {
        User user = User.create("member1", "member1@example.com", encoder.encode(RAW_PASSWORD), Role.MEMBER);
        int before = user.getTokenVersion();

        user.changePassword(encoder.encode("NewPass!2026"));

        assertThat(encoder.matches(RAW_PASSWORD, user.getPasswordHash())).isFalse();
        assertThat(encoder.matches("NewPass!2026", user.getPasswordHash())).isTrue();
        assertThat(user.getTokenVersion()).isEqualTo(before + 1);
    }

    @Test
    void changeRole_tokenVersion自增_旧token失效() {
        User user = User.create("member1", "member1@example.com", encoder.encode(RAW_PASSWORD), Role.MEMBER);

        user.changeRole(Role.ADMIN);

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void softDelete_标记删除并让token失效() {
        User user = User.create("member1", "member1@example.com", encoder.encode(RAW_PASSWORD), Role.MEMBER);

        user.softDelete(OffsetDateTime.now());

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void create_空密码哈希_立即失败而不是等到数据库报错() {
        assertThatThrownBy(() -> User.create("member1", "member1@example.com", "  ", Role.MEMBER))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
