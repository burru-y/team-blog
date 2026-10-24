package com.teamblog.comment;

import com.teamblog.comment.dto.CommentCreateRequest;
import com.teamblog.comment.dto.CommentResponse;
import com.teamblog.common.BusinessException;
import com.teamblog.common.ErrorCode;
import com.teamblog.learning.LearningLogRepository;
import com.teamblog.project.ProjectRepository;
import com.teamblog.project.ProjectStatus;
import com.teamblog.user.Role;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 评论：两级结构、软删、回复挂载校验。
 *
 * <p>三条规则：
 * <ol>
 *   <li>只能挂在存在的、未下架的成果或学习内容上</li>
 *   <li>回复的父评论必须属于同一个目标，且父评论本身不能是回复（不许三级）</li>
 *   <li>只有作者本人或管理员能删；删除是软删</li>
 * </ol>
 */
@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final ProjectRepository projectRepository;
    private final LearningLogRepository learningLogRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public CommentService(CommentRepository commentRepository,
                          ProjectRepository projectRepository,
                          LearningLogRepository learningLogRepository,
                          UserRepository userRepository,
                          Clock clock) {
        this.commentRepository = commentRepository;
        this.projectRepository = projectRepository;
        this.learningLogRepository = learningLogRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public CommentResponse create(UUID userId, CommentCreateRequest request) {
        CommentTargetType targetType = parseTargetType(request.targetType());
        requireTargetExists(targetType, request.targetId());

        UUID parentId = request.parentId();
        if (parentId != null) {
            Comment parent = commentRepository.findById(parentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "被回复的评论不存在"));
            if (parent.getTargetType() != targetType || !parent.getTargetId().equals(request.targetId())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "父评论不属于该内容");
            }
            if (!parent.isRoot()) {
                // 数据库触发器也会拦，但这里要给出人能看懂的 422
                throw new BusinessException(ErrorCode.RULE_VIOLATED, "只支持两级评论，不能回复一条回复");
            }
        }

        Comment comment = Comment.create(userId, targetType, request.targetId(), parentId, request.content().trim());
        commentRepository.save(comment);

        return CommentResponse.of(comment, usernameOf(userId), List.of());
    }

    /** 列表：一次取回该目标下所有评论（含已删），在内存里拼树。 */
    @Transactional(readOnly = true)
    public List<CommentResponse> list(String targetTypeRaw, UUID targetId) {
        CommentTargetType targetType = parseTargetType(targetTypeRaw);
        List<Comment> all = commentRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(targetType, targetId);

        Map<UUID, String> usernames = usernamesOf(all);
        Map<UUID, List<Comment>> repliesByParent = new HashMap<>();
        List<Comment> roots = new ArrayList<>();

        for (Comment comment : all) {
            if (comment.isRoot()) {
                roots.add(comment);
            } else {
                repliesByParent.computeIfAbsent(comment.getParentId(), key -> new ArrayList<>()).add(comment);
            }
        }

        List<CommentResponse> result = new ArrayList<>();
        for (Comment root : roots) {
            List<CommentResponse> replies = repliesByParent.getOrDefault(root.getId(), List.of()).stream()
                    .filter(reply -> !reply.isDeleted())          // 已删的回复直接不显示
                    .map(reply -> CommentResponse.of(reply, usernames.get(reply.getUserId()), List.of()))
                    .toList();

            if (root.isDeleted() && replies.isEmpty()) {
                continue;                                     // 已删且没有回复：整条不返回
            }
            result.add(CommentResponse.of(root, usernames.get(root.getUserId()), replies));
        }
        return result;
    }

    @Transactional
    public void delete(UUID actorId, Role actorRole, UUID commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "评论不存在"));

        if (actorRole != Role.ADMIN && !comment.isOwnedBy(actorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己的评论");
        }
        comment.softDelete(OffsetDateTime.now(clock));
    }

    private void requireTargetExists(CommentTargetType targetType, UUID targetId) {
        switch (targetType) {
            case PROJECT -> projectRepository.findByIdAndStatus(targetId, ProjectStatus.ACTIVE)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "成果不存在或已下架"));
            case LEARNING_LOG -> learningLogRepository.findById(targetId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "学习内容不存在"));
        }
    }

    private Map<UUID, String> usernamesOf(List<Comment> comments) {
        Set<UUID> ids = comments.stream().map(Comment::getUserId).collect(Collectors.toSet());
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
    }

    private String usernameOf(UUID userId) {
        return userRepository.findById(userId).map(User::getUsername).orElse("unknown");
    }

    private static CommentTargetType parseTargetType(String raw) {
        try {
            return CommentTargetType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "targetType 只能是 PROJECT 或 LEARNING_LOG");
        }
    }
}
