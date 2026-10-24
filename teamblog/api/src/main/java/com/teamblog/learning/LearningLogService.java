package com.teamblog.learning;

import com.teamblog.common.BusinessException;
import com.teamblog.common.ErrorCode;
import com.teamblog.learning.dto.LearningLogCreateRequest;
import com.teamblog.learning.dto.LearningLogCreatedResponse;
import com.teamblog.learning.dto.LearningLogResponse;
import com.teamblog.learning.dto.LearningLogUpdateRequest;
import com.teamblog.points.PointEventKind;
import com.teamblog.points.PointService;
import com.teamblog.user.Role;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 学习内容与"每日计分上限"。
 *
 * <p>规则（需求 v1.0 §2.4）：每篇 +10 分，**每天最多计 3 篇**，第 4 篇起只入库不加分。
 *
 * <p>上限为什么按"篇数"而不是"总字数"：字数可以刷（复制粘贴），篇数至少要有一次
 * 真实的提交动作；而且它是个整数，边界清楚、容易测。
 *
 * <p>并发下两个人同时发第 3、4 篇，可能出现边界抖动——最多多算一篇 10 分。
 * 这是可接受的（积分不是钱），如果要严格，得给用户行加锁。这里用注释说明取舍，
 * 而不是假装它是精确的。
 */
@Service
public class LearningLogService {

    /** 每天计入积分的篇数上限。 */
    private static final int DAILY_SCORING_LIMIT = 3;

    private final LearningLogRepository learningLogRepository;
    private final UserRepository userRepository;
    private final PointService pointService;
    private final Clock clock;

    public LearningLogService(LearningLogRepository learningLogRepository,
                              UserRepository userRepository,
                              PointService pointService,
                              Clock clock) {
        this.learningLogRepository = learningLogRepository;
        this.userRepository = userRepository;
        this.pointService = pointService;
        this.clock = clock;
    }

    @Transactional
    public LearningLogCreatedResponse create(UUID userId, LearningLogCreateRequest request) {
        LearningLogType type = parseType(request.type());
        LocalDate today = LocalDate.now(clock);

        // 先数"今天之前已发了几篇"，再插入——插入之后再数会把本次算进去
        long alreadyToday = learningLogRepository.countByUserIdAndLogDate(userId, today);

        LearningLog log = LearningLog.create(userId, type,
                request.title().trim(), request.contentMd(),
                isBlank(request.summaryMd()) ? null : request.summaryMd().trim(),
                today);
        learningLogRepository.save(log);

        boolean counted = false;
        if (alreadyToday < DAILY_SCORING_LIMIT) {
            // refId 用内容 id：同一条内容永远只能记一次分（数据库唯一约束兜底）
            counted = pointService.award(userId, PointEventKind.LEARNING_LOG,
                    log.getId().toString(), PointService.LEARNING_LOG_POINTS);
        }

        int remaining = (int) Math.max(0, DAILY_SCORING_LIMIT - (alreadyToday + 1));
        return new LearningLogCreatedResponse(
                LearningLogResponse.from(log, usernameOf(userId)),
                counted,
                counted ? PointService.LEARNING_LOG_POINTS : 0,
                remaining);
    }

    @Transactional
    public LearningLogResponse update(UUID actorId, Role actorRole, UUID logId, LearningLogUpdateRequest request) {
        LearningLog log = learningLogRepository.findById(logId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "学习内容不存在"));

        if (actorRole != Role.ADMIN && !log.isOwnedBy(actorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能修改自己发布的内容");
        }

        log.updateDetails(request.title().trim(), request.contentMd(),
                isBlank(request.summaryMd()) ? null : request.summaryMd().trim());
        return LearningLogResponse.from(log, usernameOf(log.getUserId()));
    }

    @Transactional(readOnly = true)
    public LearningLogResponse detail(UUID logId) {
        LearningLog log = learningLogRepository.findById(logId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "学习内容不存在"));
        return LearningLogResponse.from(log, usernameOf(log.getUserId()));
    }

    @Transactional(readOnly = true)
    public List<LearningLogResponse> listByUser(String username) {
        UUID userId = userRepository.findActiveByUsername(User.normalizeUsername(username))
                .map(User::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        return learningLogRepository.findByUserIdOrderByLogDateDescCreatedAtDesc(userId).stream()
                .map(log -> LearningLogResponse.from(log, username))
                .toList();
    }

    private String usernameOf(UUID userId) {
        return userRepository.findById(userId).map(User::getUsername).orElse("unknown");
    }

    private static LearningLogType parseType(String raw) {
        try {
            return LearningLogType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "type 只能是 NOTE 或 CODE");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
