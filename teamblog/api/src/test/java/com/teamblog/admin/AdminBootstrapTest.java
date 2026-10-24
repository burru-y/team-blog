package com.teamblog.admin;

import com.teamblog.user.Role;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 首启引导（批次 8.1）。
 *
 * <p>三条规则各有一个用例：空库+有密码 → 建；已有用户 → 跳过；没有密码 → 不建。
 * 用 mock 而不是集成测试：不想为了测这个把测试库里的用户全删掉。
 */
class AdminBootstrapTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void 空库且有密码_创建管理员() {
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashed");
        var bootstrap = new AdminBootstrap(userRepository, passwordEncoder,
                new AdminBootstrapProperties("root", "S0me-Str0ng-Pass"));

        bootstrap.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("root");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$12$hashed");
    }

    @Test
    void 已有用户_完全跳过() {
        when(userRepository.count()).thenReturn(1L);
        var bootstrap = new AdminBootstrap(userRepository, passwordEncoder,
                new AdminBootstrapProperties("root", "S0me-Str0ng-Pass"));

        bootstrap.run(null);

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 没有密码_绝不创建默认账号() {
        when(userRepository.count()).thenReturn(0L);
        var bootstrap = new AdminBootstrap(userRepository, passwordEncoder,
                new AdminBootstrapProperties("root", ""));

        bootstrap.run(null);

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 用户名缺省时用_admin() {
        assertThat(new AdminBootstrapProperties(null, "x").username()).isEqualTo("admin");
        assertThat(new AdminBootstrapProperties("  ", "x").username()).isEqualTo("admin");
    }
}