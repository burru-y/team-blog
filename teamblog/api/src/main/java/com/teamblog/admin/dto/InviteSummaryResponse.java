package com.teamblog.admin.dto;

import com.teamblog.admin.AdminInvite;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 邀请列表的一行。
 *
 * <p>不含 token：库里只存哈希，链接无法还原。明文只在创建时返回一次，
 * 这一点要在界面上提示管理员"复制保存"，否则关掉页面就再也拿不到。
 */
public record InviteSummaryResponse(
        UUID id,
        String email,
        String role,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        OffsetDateTime usedAt,
        OffsetDateTime revokedAt
) {
    public static InviteSummaryResponse from(AdminInvite invite, OffsetDateTime now) {
        return new InviteSummaryResponse(
                invite.getId(),
                invite.getEmail(),
                invite.getRole().name(),
                invite.status(now).name(),
                invite.getCreatedAt(),
                invite.getExpiresAt(),
                invite.getUsedAt(),
                invite.getRevokedAt());
    }
}