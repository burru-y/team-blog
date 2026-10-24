package com.teamblog.points;

import com.teamblog.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 积分对账（实施方案 §4.3 末段）。
 *
 * <p>结构上 {@code users.total_points} 是**缓存**，{@code point_events} 才是**事实**。
 * 缓存可能因为异常、并发 bug 或手工改库而分叉，分叉之后用户分数就再也对不上——
 * 这就是"为什么要有账本"的答案：有账本才谈得上对账。
 *
 * <p>策略：发现差异 → 打 ERROR 日志（带明细，方便事后追查）→ 按账本修正。
 * 只告警不修会让用户一直看着错的分；只修不告警则永远发现不了真正的 bug。
 */
@Slf4j
@Service
public class PointReconciliationService {

    private final UserRepository userRepository;

    public PointReconciliationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 每天 03:10 跑一次（北京时间）。放在凌晨是为了避开打卡与发内容的高峰。 */
    @Scheduled(cron = "0 10 3 * * *", zone = "Asia/Shanghai")
    public void scheduledReconcile() {
        try {
            ReconciliationReport report = reconcile();
            if (report.mismatchCount() > 0) {
                log.warn("积分对账完成：修正 {} 个不一致的账户", report.mismatchCount());
            } else {
                log.info("积分对账完成：无差异");
            }
        } catch (RuntimeException ex) {
            // 定时任务里抛异常会静默消失（Scheduled 不会重试），必须自己兜住并记录
            log.error("积分对账任务失败", ex);
        }
    }

    /**
     * 执行一次对账。
     *
     * @return 报告（差异明细），可用于测试与运维手动触发
     */
    @Transactional
    public ReconciliationReport reconcile() {
        List<UserRepository.PointsMismatch> mismatches = userRepository.findPointsMismatches();
        List<ReconciliationItem> items = new ArrayList<>(mismatches.size());

        for (UserRepository.PointsMismatch row : mismatches) {
            int computed = (int) row.getComputed();
            log.error("积分不一致 userId={} 记录值={} 账本汇总={}，按账本修正",
                    row.getId(), row.getRecorded(), computed);
            userRepository.setTotalPoints(row.getId(), computed);
            items.add(new ReconciliationItem(row.getId(), row.getRecorded(), computed));
        }

        return new ReconciliationReport(items.size(), items);
    }

    /** 一条差异：改之前是多少、账本算出来应该是多少。 */
    public record ReconciliationItem(java.util.UUID userId, int recorded, int computed) {
    }

    /** 对账报告：修正条数 + 明细。 */
    public record ReconciliationReport(int mismatchCount, List<ReconciliationItem> items) {
    }
}
