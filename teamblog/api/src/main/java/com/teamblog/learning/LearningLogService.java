package com.teamblog.learning;

import com.teamblog.ai.AiSummaryClient;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
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
@Slf4j
@Service
public class LearningLogService {

    /** 每天计入积分的篇数上限。 */
    private static final int DAILY_SCORING_LIMIT = 3;

    private final LearningLogRepository learningLogRepository;
    private final UserRepository userRepository;
    private final PointService pointService;
    private final AiSummaryClient aiSummaryClient;
    private final Clock clock;

    public LearningLogService(LearningLogRepository learningLogRepository,
                              UserRepository userRepository,
                              PointService pointService,
                              AiSummaryClient aiSummaryClient,
                              Clock clock) {
        this.learningLogRepository = learningLogRepository;
        this.userRepository = userRepository;
        this.pointService = pointService;
        this.aiSummaryClient = aiSummaryClient;
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
                request.tags(), today);
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
                isBlank(request.summaryMd()) ? null : request.summaryMd().trim(),
                request.tags());
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

    /**
     * 全站最新笔记。
     *
     * <p>首页只展示一小段，所以直接按 limit 取，不做游标分页——
     * 真正要翻全部的时候应该走 /learning 列表页，那里才需要分页。
     */
    @Transactional(readOnly = true)
    public List<LearningLogResponse> listLatest(int limit) {
        int capped = Math.max(1, Math.min(limit, 20));
        return learningLogRepository
                .findAllByOrderByLogDateDescCreatedAtDesc(PageRequest.of(0, capped)).stream()
                .map(log -> LearningLogResponse.from(log, usernameOf(log.getUserId())))
                .toList();
    }

    /**
     * 笔记列表页：关键词 / 标签 / 作者组合筛选。
     *
     * <p>参数全部为空时等同于「全部笔记」，所以列表页不需要另开一个接口。
     * 关键词做了 lower()，中文不受影响，英文大小写不敏感。
     */
    @Transactional(readOnly = true)
    public List<LearningLogResponse> listFiltered(String keyword, String tag, String username) {
        String normalizedKeyword = isBlank(keyword) ? null : "%" + keyword.trim().toLowerCase() + "%";
        String normalizedTag = isBlank(tag) ? null : "%" + tag.trim() + "%";
        UUID userId = null;
        if (!isBlank(username)) {
            userId = userRepository.findActiveByUsername(User.normalizeUsername(username))
                    .map(User::getId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        }

        return learningLogRepository.search(normalizedKeyword, normalizedTag, userId).stream()
                .map(log -> LearningLogResponse.from(log, usernameOf(log.getUserId())))
                .toList();
    }

    /**
     * 生成摘要（不落库）。
     *
     * <p>权限：作者本人或管理员。摘要会读到正文，让别人对他人未定稿的内容生成摘要没有意义。
     *
     * <p>AI 不可用时抛 502：这是**上游**故障，不是用户操作错误，
     * 用 500 会让人以为后端崩了，用 400 会让人去改输入。
     */
    // 不能是 readOnly：要写回摘要。之前漏了这一点，接口返回了摘要但库里一直是空。
    @Transactional
    public String summarize(UUID actorId, Role actorRole, UUID logId) {
        // 变量名不能叫 log：会遮蔽 @Slf4j 生成的静态 logger，编译期报
        // "cannot find symbol: method warn(...) on LearningLog"
        LearningLog entry = learningLogRepository.findById(logId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "学习内容不存在"));

        if (actorRole != Role.ADMIN && !entry.isOwnedBy(actorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能为自己发布的内容生成摘要");
        }

        try {
            String summary = aiSummaryClient.summarize(entry.getContentMd());
            entry.applySummary(summary);
            return summary;
        } catch (AiSummaryClient.AiUnavailableException ex) {
            // 对外只说"暂时不可用"，但服务端必须留下真实原因（S-09）：
            // 否则排查时只能看到 503，连是超时、签名错还是解析失败都分不清
            log.warn("摘要生成失败：{}", ex.getMessage(), ex);
            throw new BusinessException(ErrorCode.UPSTREAM_FAILED, "AI 服务暂时不可用，请稍后再试");
        }
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
