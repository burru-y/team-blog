package com.teamblog.common;

/**
 * 触发限流。比 {@link BusinessException} 多带一个"多久之后可以再试"。
 *
 * <p>为什么单独一个类型：429 必须带 {@code Retry-After} 头（A-13），而普通业务异常
 * 没有这个信息。用子类承载，GlobalExceptionHandler 才能在不解析字符串的前提下补头。
 */
public class RateLimitException extends BusinessException {

    private final long retryAfterSeconds;

    public RateLimitException(long retryAfterSeconds, String message) {
        super(ErrorCode.RATE_LIMITED, message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
