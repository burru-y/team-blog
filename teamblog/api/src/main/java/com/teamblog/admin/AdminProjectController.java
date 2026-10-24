package com.teamblog.admin;

import com.teamblog.common.ApiResponse;
import com.teamblog.common.BusinessException;
import com.teamblog.common.ErrorCode;
import com.teamblog.project.Project;
import com.teamblog.project.ProjectRepository;
import com.teamblog.project.ProjectStatus;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 管理端成果列表：**包含已下架的**。
 *
 * <p>前台列表只给 ACTIVE（下架就当不存在），但管理端必须能看到 REMOVED——
 * 否则"下架"就变成"再也找不回来"，与软下架的初衷矛盾。
 */
@RestController
@RequestMapping("/api/v1/admin/projects")
public class AdminProjectController {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public AdminProjectController(ProjectRepository projectRepository, UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<List<AdminProjectResponse>> list(@RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "100") int limit) {
        var pageable = PageRequest.of(0, Math.min(Math.max(limit, 1), 200));
        List<Project> rows = (status == null || status.isBlank())
                ? projectRepository.findAllByOrderByCreatedAtDesc(pageable)
                : projectRepository.findByStatusOrderByCreatedAtDesc(parseStatus(status), pageable);

        Map<UUID, String> usernames = userRepository.findAllById(
                        rows.stream().map(Project::getOwnerId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));

        return ApiResponse.ok(rows.stream()
                .map(project -> new AdminProjectResponse(
                        project.getId(),
                        project.getType().name(),
                        project.getTitle(),
                        usernames.getOrDefault(project.getOwnerId(), "unknown"),
                        project.getStatus().name(),
                        project.getCreatedAt(),
                        project.getRemovedAt()))
                .toList());
    }

    private static ProjectStatus parseStatus(String raw) {
        try {
            return ProjectStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "status 只能是 ACTIVE 或 REMOVED");
        }
    }

    public record AdminProjectResponse(
            UUID id,
            String type,
            String title,
            String ownerUsername,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime removedAt
    ) {
    }
}
