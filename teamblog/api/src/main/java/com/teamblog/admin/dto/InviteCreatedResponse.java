package com.teamblog.admin.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 创建成功的响应：**唯一一次**能看到明文 token 的地方。
 *
 * <p>前端应该把 token 拼成链接发给对方（如 `https://site/join?token=...`），
 * 之后服务端再也拿不到它——库里只有哈希。
 */
public record InviteCreatedResponse(UUID id, String token, String email, String role,
                                    OffsetDateTime expiresAt) {
}
