package com.teamblog.admin;

import com.teamblog.admin.dto.CreateInviteRequest;
import com.teamblog.admin.dto.InviteSummaryResponse;
import com.teamblog.admin.dto.InviteCreatedResponse;
import com.teamblog.auth.AuthPrincipal;
import com.teamblog.common.ApiResponse;
import com.teamblog.common.BusinessException;
import com.teamblog.common.ErrorCode;
import com.teamblog.user.Role;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
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

    /** 已签发的邀请列表（含状态）。管理员页面用。 */
    @GetMapping
    public ApiResponse<List<InviteSummaryResponse>> list() {
        OffsetDateTime now = OffsetDateTime.now();
        List<InviteSummaryResponse> items = inviteService.list().stream()
                .map(invite -> InviteSummaryResponse.from(invite, now))
                .toList();
        return ApiResponse.ok(items);
    }

    /** 撤销邀请。已使用的不能撤销。 */
    @DeleteMapping("/{id}")
    public ApiResponse<InviteSummaryResponse> revoke(@PathVariable UUID id) {
        AdminInvite invite = inviteService.revoke(id);
        return ApiResponse.ok(InviteSummaryResponse.from(invite, OffsetDateTime.now()));
    }

    private static Role parseRole(String raw) {
        try {
            return Role.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "role 只能是 ADMIN 或 MEMBER");
        }
    }
}
