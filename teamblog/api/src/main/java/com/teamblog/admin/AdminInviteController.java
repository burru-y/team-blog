package com.teamblog.admin;

import com.teamblog.admin.dto.CreateInviteRequest;
import com.teamblog.admin.dto.InviteCreatedResponse;
import com.teamblog.auth.AuthPrincipal;
import com.teamblog.common.ApiResponse;
import com.teamblog.common.BusinessException;
import com.teamblog.common.ErrorCode;
import com.teamblog.user.Role;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * 管理员创建邀请。
 *
 * <p>路径在 `/api/v1/admin/**` 下，由 SecurityConfig 的 URL 规则要求 ADMIN 角色。
 * 这里不再写一遍 if 判断——**权限规则只放一处**，重复判断最容易出现"两个地方不一致"。
 */
@RestController
@RequestMapping("/api/v1/admin/invites")
public class AdminInviteController {

    private final AdminInviteService inviteService;

    public AdminInviteController(AdminInviteService inviteService) {
        this.inviteService = inviteService;
    }

    @PostMapping
    public ApiResponse<InviteCreatedResponse> create(@Valid @RequestBody CreateInviteRequest request,
                                                     @AuthenticationPrincipal AuthPrincipal principal) {
        AdminInviteService.IssuedInvite issued =
                inviteService.create(principal.id(), request.email(), parseRole(request.role()));
        return ApiResponse.ok(new InviteCreatedResponse(
                issued.id(), issued.rawToken(), issued.email(),
                issued.role().name(), issued.expiresAt()));
    }

    private static Role parseRole(String raw) {
        try {
            return Role.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "role 只能是 ADMIN 或 MEMBER");
        }
    }
}
