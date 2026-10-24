package com.teamblog.points;

import com.teamblog.user.Role;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 积分对账（批次 4.3 后半）。
 *
 * <p>要验证的不是"能跑"，而是：分叉能被发现、能被修正、修完之后再跑一次就是 0 条。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PointReconciliationTest {

    @Autowired
    private PointReconciliationService reconciliationService;

    @Autowired
    private PointEventRepository pointEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void 记账一致时_没有差异() {
        User user = persistUser();
        pointEventRepository.saveAndFlush(PointEvent.of(user.getId(), PointEventKind.CHECKIN, "ref-1", 1));
        userRepository.addPoints(user.getId(), 1);

        var report = reconciliationService.reconcile();

        assertThat(report.mismatchCount()).isZero();
        assertThat(report.items()).isEmpty();
    }

    @Test
    void 总分被改错时_对账发现并按账本修正() {
        User user = persistUser();
        pointEventRepository.saveAndFlush(PointEvent.of(user.getId(), PointEventKind.CHECKIN, "ref-1", 1));
        pointEventRepository.saveAndFlush(PointEvent.of(user.getId(), PointEventKind.LEARNING_LOG, "ref-2", 10));
        pointEventRepository.saveAndFlush(
                PointEvent.of(user.getId(), PointEventKind.STREAK_7, "ref-3", 5));

        // 模拟一次漏更新：账本 16 分，缓存却只写了 3 分
        userRepository.setTotalPoints(user.getId(), 3);

        var report = reconciliationService.reconcile();

        assertThat(report.mismatchCount()).isEqualTo(1);
        assertThat(report.items().get(0).recorded()).isEqualTo(3);
        assertThat(report.items().get(0).computed()).isEqualTo(16);
        assertThat(userRepository.findTotalPointsById(user.getId())).contains(16);
    }

    @Test
    void 修完之后再跑一次_报告为0条() {
        User user = persistUser();
        pointEventRepository.saveAndFlush(PointEvent.of(user.getId(), PointEventKind.CHECKIN, "ref-1", 1));
        userRepository.setTotalPoints(user.getId(), 999);

        assertThat(reconciliationService.reconcile().mismatchCount()).isEqualTo(1);
        assertThat(reconciliationService.reconcile().mismatchCount()).isZero();
    }

    @Test
    void 没有任何积分事件的人_按0对账() {
        User user = persistUser();
        // 没有任何 point_events，但 total_points 被写成了 50（例如种子数据或手工改库）
        userRepository.setTotalPoints(user.getId(), 50);

        var report = reconciliationService.reconcile();

        assertThat(report.mismatchCount()).isEqualTo(1);
        assertThat(userRepository.findTotalPointsById(user.getId())).contains(0);
    }

    private User persistUser() {
        String username = ("rec" + UUID.randomUUID().toString().replace("-", "").substring(0, 6)).toLowerCase();
        return userRepository.saveAndFlush(User.create(
                username, username + "@example.com", passwordEncoder.encode("S3cret!pass"), Role.MEMBER));
    }
}
