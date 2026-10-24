package com.teamblog.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

/**
 * 管理员创建邀请的请求体。
 *
 * <p>role 显式要求传，不设默认值：邀请 ADMIN 和邀请 MEMBER 是完全不同的授权动作，
 * 用默认值容易"本来只想拉个成员，手滑拉成了管理员"。
 */
public record CreateInviteRequest(
        @Email(message = "格式不正确") String email,
        @NotNull(message = "不能为空") String role
) {
}
