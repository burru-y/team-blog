package com.teamblog.auth.dto;

import java.time.OffsetDateTime;

/**
 * 登录/刷新的响应体：只含 access token。
 *
 * <p>为什么 refresh token 不在这里：它必须走 httpOnly Cookie（A-24）。放进 JSON 就意味
 * 前端要用 JS 读它，也就意味着一次 XSS 就能把 7 天的会话偷走。
 *
 * <p>expiresAt 是给前端做"提前 1 分钟刷新"用的，不是用来判断过期的唯一依据。
 */
public record TokenResponse(String accessToken, OffsetDateTime expiresAt) {
}
