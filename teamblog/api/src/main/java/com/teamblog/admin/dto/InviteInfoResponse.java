package com.teamblog.admin.dto;

import java.time.OffsetDateTime;

/**
 * 注册页用：告诉用户"这个邀请是给谁的、什么时候过期"。
 *
 * <p>不含 token 本身，也不含创建人——注册页不需要这些信息，少返回就少一份泄漏面。
 */
public record InviteInfoResponse(String email, String role, OffsetDateTime expiresAt) {
}
