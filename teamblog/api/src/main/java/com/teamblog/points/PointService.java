package com.teamblog.points;

import com.teamblog.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 发积分：写一条账本 + 原子增加用户总分。
 *
 * <p>为什么总分用 {@code UPDATE ... SET total_points = total_points + ?} 而不是
 * 先查再加：并发下"读-改-写"会丢分（两个请求同时读到 10，都写 20，实际应为 30）。
 * 一条 SQL 由数据库保证原子性。
 *
 * <p>为什么还要先查一次 {@code existsBy...}：省一次注定失败的唯一约束冲突（异常开销大）。
 * 但它只是优化——**真正的"只加一次"由唯一约束保证**，并发下谁先插谁赢。
 */
@Service
public class PointService {

    /** 学习内容每篇 10 分（需求 v1.0 §2.4，写死不做配置）。 */
    public static final int LEARNING_LOG_POINTS = 10;

    private final PointEventRepository pointEventRepository;
    private final UserRepository userRepository;

    public PointService(PointEventRepository pointEventRepository, UserRepository userRepository) {
        this.pointEventRepository = pointEventRepository;
        this.userRepository = userRepository;
    }

    /**
     * 记一次积分。
     *
     * @return true 表示这次真的加了；false 表示同一事件已经记过（幂等）
     */
    @Transactional
    public boolean award(UUID userId, PointEventKind kind, String refId, int points) {
        if (pointEventRepository.existsByUserIdAndKindAndRefId(userId, kind, refId)) {
            return false;
        }
        // 不 try/catch 唯一约束冲突：一旦 flush 失败，当前事务已被标记 rollback-only，
        // 吞掉异常也提交不了。真要处理并发重复，只能把插入放到 REQUIRES_NEW 的独立事务里。
        pointEventRepository.saveAndFlush(PointEvent.of(userId, kind, refId, points));
        userRepository.addPoints(userId, points);
        return true;
    }
}
