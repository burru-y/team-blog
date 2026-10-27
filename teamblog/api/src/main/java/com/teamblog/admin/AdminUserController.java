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
import com.teamblog.user.UserService;
import com.teamblog.user.dto.ResetPasswordRequest;
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
    private final UserService userService;

    public AdminUserController(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
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

    /** 重置密码：改完旧会话立刻失效（tokenVersion +1）。 */
    @PostMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable UUID id,
                                           @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(principal.id(), id, request.newPassword());
        return ApiResponse.ok(null);
    }

    /**
     * 注销账号（软删除）。
     *
     * <p>为什么不物理删除：笔记、评论、积分流水都引用这个用户，
     * 真删会让历史记录缺主。软删除后账号不能再登录，但内容仍在。
     *
     * <p>admin1 是引导账号：任何管理员都不能注销它（包括管理员之间互相操作），
     * 否则一旦误删就没人能进后台恢复。
     */
    @PostMapping("/{id}/deactivate")
    @Transactional
    public ApiResponse<Void> deactivate(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable UUID id) {
        if (principal.id().equals(id)) {
            throw new BusinessException(ErrorCode.RULE_VIOLATED, "不能注销自己");
        }

        User user = userRepository.findById(id)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        if ("admin1".equals(user.getUsername())) {
            throw new BusinessException(ErrorCode.RULE_VIOLATED, "admin1 是引导账号，不可注销");
        }

        user.softDelete(java.time.OffsetDateTime.now(java.time.ZoneId.of("Asia/Shanghai")));
        return ApiResponse.ok(null);
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
