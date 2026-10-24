package com.teamblog.admin;

import com.teamblog.auth.AuthPrincipal;
import com.teamblog.common.ApiResponse;
import com.teamblog.common.BusinessException;
import com.teamblog.common.ErrorCode;
import com.teamblog.project.ProjectStatus;
import com.teamblog.user.Role;
import com.teamblog.user.User;
import com.teamblog.user.UserRepository;
import com.teamblog.user.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 管理端成员接口：列表 + 改角色。
 *
 * <p>为什么改角色要 bump tokenVersion：JWT 里带着 `role` 和 `tv`，令牌一旦签发就固定了。
 * 如果只改库不 bump，被降级的用户手里那个旧 token 还是 ADMIN，直到过期为止（最长 30 分钟）。
 * bump 之后旧 token 立刻失效，下次请求返回 SESSION_REVOKED。
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserRepository userRepository;

    public AdminUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<List<UserResponse>> list(@RequestParam(defaultValue = "100") int limit) {
        var pageable = PageRequest.of(0, Math.min(Math.max(limit, 1), 200));
        List<UserResponse> users = userRepository.findFirstPage(pageable).stream()
                .map(UserResponse::from)
                .toList();
        return ApiResponse.ok(users);
    }

    @PatchMapping("/{id}/role")
    @Transactional
    public ApiResponse<UserResponse> changeRole(@AuthenticationPrincipal AuthPrincipal principal,
                                                @PathVariable UUID id,
                                                @Valid @RequestBody ChangeRoleRequest request) {
        if (principal.id().equals(id)) {
            // 自己给自己降级会把自己锁在门外，也容易误操作：明确拒绝
            throw new BusinessException(ErrorCode.RULE_VIOLATED, "不能修改自己的角色");
        }

        User user = userRepository.findById(id)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        user.changeRole(parseRole(request.role()));   // 内部会 bump tokenVersion
        return ApiResponse.ok(UserResponse.from(user));
    }

    private static Role parseRole(String raw) {
        try {
            return Role.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "role 只能是 ADMIN 或 MEMBER");
        }
    }

    public record ChangeRoleRequest(@NotBlank(message = "不能为空") String role) {
    }
}
