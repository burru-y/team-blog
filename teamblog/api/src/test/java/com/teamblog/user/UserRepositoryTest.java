package com.teamblog.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 实体到 PostgreSQL 的真实往返。
 *
 * <p>为什么不能只靠单测：uuid、varchar+enum、timestamptz 这三类映射的错法只有真写一次
 * 才暴露——{@code ddl-auto=validate} 只校验列存在与类型，不校验驱动层能否往返。
 *
 * <p>每个用例自带数据并在事务里回滚（Q-09）：不依赖库里已有的种子，也不给下一次运行
 * 留脏数据。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void save_完整字段_能从数据库读回且值不变() {
        User saved = userRepository.saveAndFlush(User.create(
                "member9", "member9@example.com", passwordEncoder.encode("S3cret!pass"), Role.MEMBER));

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getUsername()).isEqualTo("member9");
        assertThat(reloaded.getEmail()).isEqualTo("member9@example.com");
        assertThat(reloaded.getRole()).isEqualTo(Role.MEMBER);
        assertThat(reloaded.getPasswordHash()).startsWith("$2a$12$");
        assertThat(reloaded.getTokenVersion()).isZero();
        assertThat(reloaded.getLastLoginAt()).isNull();
        assertThat(reloaded.getCreatedAt()).isNotNull();   // @CreationTimestamp 生效
    }

    @Test
    void save_用户名重复_被唯一键拒绝而不是静默覆盖() {
        userRepository.saveAndFlush(User.create(
                "duplicated", "first@example.com", passwordEncoder.encode("S3cret!pass"), Role.MEMBER));

        assertThatThrownBy(() -> userRepository.saveAndFlush(User.create(
                "duplicated", "second@example.com", passwordEncoder.encode("S3cret!pass"), Role.MEMBER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findActiveByUsername_软删账号_视为不存在() {
        User user = userRepository.saveAndFlush(User.create(
                "leaving", "leaving@example.com", passwordEncoder.encode("S3cret!pass"), Role.MEMBER));

        user.softDelete(OffsetDateTime.now());
        userRepository.saveAndFlush(user);

        assertThat(userRepository.findActiveByUsername("leaving")).isEmpty();
        assertThat(userRepository.findByUsername("leaving")).isPresent();     // 后台仍可见
        assertThat(userRepository.existsByUsername("leaving")).isTrue();      // 占用仍成立
    }
}
