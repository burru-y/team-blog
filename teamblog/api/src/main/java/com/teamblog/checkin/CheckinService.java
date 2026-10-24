package com.teamblog.checkin;

import com.teamblog.checkin.dto.CheckinResult;
import com.teamblog.checkin.dto.CheckinStatus;
import com.teamblog.checkin.dto.HeatmapResponse;
import com.teamblog.common.BusinessException;
import com.teamblog.common.ErrorCode;
import com.teamblog.points.LevelCalculator;
import com.teamblog.points.PointEventKind;
import com.teamblog.points.PointService;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 打卡与连续段奖励。
 *
 * <p>这是全项目唯一"算错就难回滚"的业务，三条规则写在注释里：
 * <ol>
 *   <li>每天只能打一次——靠 {@code uq_checkins_user_date} 唯一约束，冲突返回 409</li>
 *   <li>打卡 +1 分；连续第 7/14/30 天额外 +5/+10/+20</li>
 *   <li>连续段奖励"每段只发一次"，{@code ref_id} 用**本段第一天**</li>
 * </ol>
 *
 * <p>为什么 {@code ref_id} 用本段第一天而不是当天：同一次连续里第 7、14、30 天各发一次，
 * 但同一天重复请求不会重复发（唯一约束按 (user, kind, ref_id) 判重）。断签后开启新的一段，
 * 第一天不同，于是能再拿——这正好符合"每段一次"的语义。
 */
@Service
public class CheckinService {

    /** 打卡本身的分。 */
    private static final int CHECKIN_POINTS = 1;

    /** 连续段奖励：天数 → 分数。 */
    private static final Map<Integer, Integer> STREAK_BONUS = Map.of(7, 5, 14, 10, 30, 20);

    /** 连续段判定最多回看多少天（30 天奖励 + 缓冲）。 */
    private static final int STREAK_LOOKBACK_DAYS = 60;

    private final CheckinRepository checkinRepository;
    private final PointService pointService;
    private final UserRepository userRepository;
    private final Clock clock;

    public CheckinService(CheckinRepository checkinRepository,
                          PointService pointService,
                          UserRepository userRepository,
                          Clock clock) {
        this.checkinRepository = checkinRepository;
        this.pointService = pointService;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * 打卡。整个过程在一个事务里：插打卡 → 记 +1 → 判断连续段 → 可能有奖励。
     *
     * <p>中途失败就整体回滚，不会留下"打卡了但没加分"或"加分了但没打卡"的半条数据。
     */
    @Transactional
    public CheckinResult checkin(UUID userId, String note) {
        LocalDate today = LocalDate.now(clock);

        Checkin checkin = Checkin.create(userId, today, isBlank(note) ? null : note.trim());
        try {
            // saveAndFlush：立刻撞唯一约束，好在事务里翻译成 409，而不是等到提交时炸
            checkinRepository.saveAndFlush(checkin);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.ALREADY_CHECKED_IN);
        }

        int gained = 0;
        if (pointService.award(userId, PointEventKind.CHECKIN, checkin.getId().toString(), CHECKIN_POINTS)) {
            gained += CHECKIN_POINTS;
        }

        Set<LocalDate> dates = recentDates(userId, today);
        int streak = currentStreak(dates, today);

        Integer bonus = STREAK_BONUS.get(streak);
        if (bonus != null) {
            LocalDate streakStart = today.minusDays(streak - 1L);
            PointEventKind kind = switch (streak) {
                case 7 -> PointEventKind.STREAK_7;
                case 14 -> PointEventKind.STREAK_14;
                default -> PointEventKind.STREAK_30;
            };
            if (pointService.award(userId, kind, streakStart.toString(), bonus)) {
                gained += bonus;
            }
        }

        int total = totalPointsOf(userId);
        return new CheckinResult(today, streak, gained, total, LevelCalculator.levelOf(total));
    }

    @Transactional(readOnly = true)
    public CheckinStatus status(UUID userId) {
        LocalDate today = LocalDate.now(clock);
        boolean checkedInToday = checkinRepository.existsByUserIdAndCheckinDate(userId, today);
        int streak = currentStreak(recentDates(userId, today), today);
        int total = totalPointsOf(userId);

        return new CheckinStatus(checkedInToday, today, streak, total, LevelCalculator.levelOf(total));
    }

    /**
     * 热力图数据。
     *
     * <p>给了 username 就是个人（某天打没打），没给就是团队（当天有多少人打卡）。
     * 两者返回同一个结构：日期 → 数值，前端用同一套颜色分级。
     */
    @Transactional(readOnly = true)
    public HeatmapResponse heatmap(String username, int days) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(days - 1L);
        Map<LocalDate, Integer> counts = new HashMap<>();

        if (username == null || username.isBlank()) {
            for (CheckinRepository.DailyCount row : checkinRepository.countByDateBetween(from, today)) {
                counts.put(row.getCheckinDate(), (int) row.getTotal());
            }
        } else {
            User user = userRepository.findActiveByUsername(User.normalizeUsername(username))
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
            List<Checkin> rows = checkinRepository.findByUserIdAndCheckinDateBetween(user.getId(), from, today);
            for (Checkin row : rows) {
                counts.put(row.getCheckinDate(), 1);
            }
        }

        return new HeatmapResponse(from, today, counts);
    }

    /** 一次取回近 60 天的打卡日期，避免逐天查库。 */
    private Set<LocalDate> recentDates(UUID userId, LocalDate today) {
        List<Checkin> rows = checkinRepository.findByUserIdAndCheckinDateBetween(
                userId, today.minusDays(STREAK_LOOKBACK_DAYS), today);
        Set<LocalDate> dates = new HashSet<>(rows.size());
        for (Checkin row : rows) {
            dates.add(row.getCheckinDate());
        }
        return dates;
    }

    /** 从今天往回数，遇到断档即停。 */
    private static int currentStreak(Set<LocalDate> dates, LocalDate today) {
        int streak = 0;
        LocalDate cursor = today;
        while (dates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private int totalPointsOf(UUID userId) {
        return userRepository.findTotalPointsById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
