package com.teamblog.project;

import com.teamblog.common.BusinessException;
import com.teamblog.common.CursorUtils;
import com.teamblog.common.ErrorCode;
import com.teamblog.common.PageResponse;
import com.teamblog.project.dto.ProjectCardResponse;
import com.teamblog.project.dto.ProjectCreateRequest;
import com.teamblog.project.dto.ProjectResponse;
import com.teamblog.project.dto.ProjectUpdateRequest;
import com.teamblog.user.Role;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 成果的业务规则。
 *
 * <p>三件事都在这里，因为它们互相关联：
 * <ul>
 *   <li><b>按类型校验必填</b>：项目要正文+链接，证书/奖项至少要有图或链接</li>
 *   <li><b>归属校验</b>：成员只能改自己的，管理员都能改（S-01 的第三重）</li>
 *   <li><b>软下架</b>：下架后前台看不到，数据还在（D-13）</li>
 * </ul>
 */
@Service
public class ProjectService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public ProjectService(ProjectRepository projectRepository, UserRepository userRepository, Clock clock) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public ProjectResponse create(UUID ownerId, ProjectCreateRequest request) {
        ProjectType type = parseType(request.type());
        validateByType(type, request.githubUrl(), request.contentMd(), request.imageUrl());

        Project project = Project.create(ownerId, type, request.title().trim(), request.description().trim(),
                trimToNull(request.githubUrl()), trimToNull(request.contentMd()), trimToNull(request.imageUrl()));
        projectRepository.save(project);

        String ownerUsername = userRepository.findById(ownerId).map(User::getUsername).orElse("unknown");
        return ProjectResponse.from(project, ownerUsername);
    }

    @Transactional
    public ProjectResponse update(UUID actorId, Role actorRole, UUID projectId, ProjectUpdateRequest request) {
        Project project = requireActive(projectId);
        requireOwnerOrAdmin(project, actorId, actorRole);
        validateByType(project.getType(), request.githubUrl(), request.contentMd(), request.imageUrl());

        project.updateDetails(request.title().trim(), request.description().trim(),
                trimToNull(request.githubUrl()), trimToNull(request.contentMd()), trimToNull(request.imageUrl()));

        String ownerUsername = userRepository.findById(project.getOwnerId()).map(User::getUsername).orElse("unknown");
        return ProjectResponse.from(project, ownerUsername);
    }

    @Transactional
    public void remove(UUID actorId, Role actorRole, UUID projectId) {
        Project project = requireActive(projectId);
        requireOwnerOrAdmin(project, actorId, actorRole);
        project.remove(OffsetDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectCardResponse> list(String cursor, int limit) {
        int size = normalizeLimit(limit);
        List<Project> rows = (cursor == null || cursor.isBlank())
                ? projectRepository.findFirstPage(ProjectStatus.ACTIVE, PageRequest.of(0, size + 1))
                : pageAfterCursor(cursor, size);

        boolean hasNext = rows.size() > size;
        List<Project> page = hasNext ? rows.subList(0, size) : rows;
        Map<UUID, String> usernames = usernamesOf(page);

        String nextCursor = hasNext
                ? CursorUtils.encode(page.get(page.size() - 1).getCreatedAt(), page.get(page.size() - 1).getId())
                : null;

        List<ProjectCardResponse> items = page.stream()
                .map(project -> ProjectCardResponse.from(project, usernames.getOrDefault(project.getOwnerId(), "unknown")))
                .toList();

        return PageResponse.of(items, nextCursor);
    }

    /** 前台只能看到上架的成果；已下架按"不存在"处理（404），不暴露"存在但被下架"。 */
    @Transactional(readOnly = true)
    public ProjectResponse detail(UUID projectId) {
        Project project = requireActive(projectId);
        String ownerUsername = userRepository.findById(project.getOwnerId()).map(User::getUsername).orElse("unknown");
        return ProjectResponse.from(project, ownerUsername);
    }

    private List<Project> pageAfterCursor(String cursor, int size) {
        CursorUtils.Cursor decoded = CursorUtils.decode(cursor);
        return projectRepository.findPageAfter(ProjectStatus.ACTIVE, decoded.createdAt(), decoded.id(),
                PageRequest.of(0, size + 1));
    }

    private Project requireActive(UUID projectId) {
        return projectRepository.findByIdAndStatus(projectId, ProjectStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "成果不存在或已下架"));
    }

    /**
     * S-01 的第三重：资源归属。
     *
     * <p>URL 级规则只能表达"登录了没""是不是管理员"，表达不了"这条是不是你的"——
     * 后者依赖数据，必须在 Service 里判断。
     */
    private void requireOwnerOrAdmin(Project project, UUID actorId, Role actorRole) {
        if (actorRole == Role.ADMIN) {
            return;
        }
        if (!project.isOwnedBy(actorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能修改自己发布的成果");
        }
    }

    /** 按类型校验必填（与 V1 的 ck_projects_required 同义，但给出人能看懂的提示）。 */
    private void validateByType(ProjectType type, String githubUrl, String contentMd, String imageUrl) {
        switch (type) {
            case PROJECT -> {
                if (isBlank(githubUrl)) {
                    throw new BusinessException(ErrorCode.RULE_VIOLATED, "项目类成果必须填仓库链接");
                }
                if (isBlank(contentMd)) {
                    throw new BusinessException(ErrorCode.RULE_VIOLATED, "项目类成果必须填正文");
                }
            }
            case CERTIFICATE, AWARD -> {
                if (isBlank(githubUrl) && isBlank(imageUrl)) {
                    throw new BusinessException(ErrorCode.RULE_VIOLATED, "证书/奖项至少要有一张图或一个链接");
                }
            }
        }
    }

    private static ProjectType parseType(String raw) {
        try {
            return ProjectType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "type 只能是 PROJECT / CERTIFICATE / AWARD");
        }
    }

    /** 批量取用户名，避免列表逐条查库（N+1）。 */
    private Map<UUID, String> usernamesOf(List<Project> projects) {
        Set<UUID> ownerIds = projects.stream().map(Project::getOwnerId).collect(Collectors.toSet());
        return userRepository.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
