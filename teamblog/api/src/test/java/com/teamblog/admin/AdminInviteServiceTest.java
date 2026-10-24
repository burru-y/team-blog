package com.teamblog.admin;

import com.teamblog.common.BusinessException;
import com.teamblog.common.ErrorCode;
import com.teamblog.user.Role;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 邀请的签发与兑换（1.5 单测）。
 *
 * <p>并发（同一瞬间两个人兑换）不在这里测：它依赖数据库行锁，必须用两个真实事务；
 * 这里用 {@code markUsedIfUnused} 的返回值单独验证"条件更新只会成功一次"。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminInviteServiceTest {

    @Autowired
    private AdminInviteService inviteService;

    @Autowired
    private AdminInviteRepository inviteRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 创建后_明文token能换出可用邀请() {
        User admin = persistUser("inviteadmin");

        AdminInviteService.IssuedInvite issued =
                inviteService.create(admin.getId(), "newbie@example.com", Role.MEMBER);

        assertThat(issued.rawToken()).isNotBlank();
        assertThat(issued.email()).isEqualTo("newbie@example.com");

        AdminInvite loaded = inviteService.requireUsable(issued.rawToken());
        assertThat(loaded.isUsed()).isFalse();
        assertThat(loaded.getRole()).isEqualTo(Role.MEMBER);
    }

    @Test
    void 兑换_创建账号并沿用邀请里的角色和邮箱() {
        User admin = persistUser("inviteadmin2");
        var issued = inviteService.create(admin.getId(), "member9@example.com", Role.MEMBER);

        User created = inviteService.accept(issued.rawToken(), "member9", "S3cret!pass", null);

        assertThat(created.getUsername()).isEqualTo("member9");
        assertThat(created.getEmail()).isEqualTo("member9@example.com");
        assertThat(created.getRole()).isEqualTo(Role.MEMBER);
        assertThat(userRepository.findById(created.getId())).isPresent();
    }

    @Test
    void 邀请指定了邮箱_请求里的邮箱被忽略() {
        User admin = persistUser("inviteadmin3");
        var issued = inviteService.create(admin.getId(), "real@example.com", Role.MEMBER);

        User created = inviteService.accept(issued.rawToken(), "realuser", "S3cret!pass", "fake@example.com");

        assertThat(created.getEmail()).isEqualTo("real@example.com");
    }

    @Test
    void 邀请没指定邮箱_请求必须填() {
        User admin = persistUser("inviteadmin4");
        var issued = inviteService.create(admin.getId(), null, Role.MEMBER);

        assertThatThrownBy(() -> inviteService.accept(issued.rawToken(), "noemail", "S3cret!pass", null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void 过期的邀请_抛INVITE_INVALID() {
        User admin = persistUser("inviteadmin5");
        AdminInvite expired = AdminInvite.create(admin.getId(),
                com.teamblog.common.HashUtils.sha256Hex("expired-invite"),
                null, Role.MEMBER,
                OffsetDateTime.now().minusDays(30), java.time.Duration.ofDays(7));
        inviteRepository.save(expired);

        assertThatThrownBy(() -> inviteService.requireUsable("expired-invite"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.INVITE_INVALID);
    }

    @Test
    void 条件更新_第二次占用返回零() {
        User admin = persistUser("inviteadmin6");
        var issued = inviteService.create(admin.getId(), null, Role.MEMBER);
        AdminInvite invite = inviteService.requireUsable(issued.rawToken());

        int first = inviteRepository.markUsedIfUnused(invite.getId(), OffsetDateTime.now());
        int second = inviteRepository.markUsedIfUnused(invite.getId(), OffsetDateTime.now());

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();   // 数据库保证只有一次能成功
    }

    private User persistUser(String username) {
        String name = username + UUID.randomUUID().toString().substring(0, 4).toLowerCase();
        return userRepository.saveAndFlush(User.create(
                name, name + "@example.com", "$2a$12$not-a-real-hash", Role.ADMIN));
    }
}
