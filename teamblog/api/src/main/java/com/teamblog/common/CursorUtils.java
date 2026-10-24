package com.teamblog.common;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

/**
 * 游标编解码。
 *
 * <p>格式：`瞬时时间|UUID`，整体 Base64URL。不加密——它只是"从哪继续"的坐标，
 * 不含隐私。用 Base64 是为了让客户端把它当成不透明字符串，而不是自己拼一个。
 *
 * <p>为什么要 <b>两个</b> 字段：{@code created_at} 不唯一（种子数据在同一个事务里插入，
 * 时间完全相同）。只按时间翻页会在边界丢行或重复；加上 id 作为第二排序键（tiebreaker），
 * 才能得到严格的全序。
 *
 * <p>为什么用 {@link Instant} 的字符串而不是毫秒数：数据库的 timestamptz 精确到微秒。
 * 截断到毫秒后，同一毫秒内的行会被错误地排除，表现为"翻页偶尔少一条"。
 */
public final class CursorUtils {

    private static final String SEPARATOR = "|";

    private CursorUtils() {
        // 工具类（J-27）
    }

    public record Cursor(OffsetDateTime createdAt, UUID id) {
    }

    public static String encode(OffsetDateTime createdAt, UUID id) {
        String raw = createdAt.toInstant() + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解析客户端传来的游标。
     *
     * @throws BusinessException 格式不对时返回 400，而不是让它变成 500
     */
    public static Cursor decode(String raw) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\" + SEPARATOR);
            if (parts.length != 2) {
                throw new IllegalArgumentException("段数不对");
            }
            return new Cursor(
                    OffsetDateTime.ofInstant(Instant.parse(parts[0]), ZoneOffset.UTC),
                    UUID.fromString(parts[1]));
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "cursor 无效");
        }
    }
}
