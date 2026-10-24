package com.teamblog.common;

/**
 * 业务失败。Service 层唯一的"正常失败"表达方式（J-16）。
 *
 * <p>为什么不返回 null 或 boolean：调用方要区分"没有权限"和"不存在"，返回 null 只能
 * 表达"没有值"；用异常能带上 {@link ErrorCode}，由 {@link GlobalExceptionHandler} 统一
 * 转成信封，Controller 里一个 if 都不用写（J-05）。
 *
 * <p>为什么继承 RuntimeException：Spring 的 @Transactional 默认只在运行时异常时回滚，
 * 受检异常会被当成"可恢复"，大概率留下半条数据。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    public BusinessException(ErrorCode code) {
        super(code.message());
        this.code = code;
    }

    public BusinessException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
