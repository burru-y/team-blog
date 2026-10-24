package com.teamblog.common;

import org.springframework.http.HttpStatus;

/**
 * 错误码全集（唯一真相：07-开发规范 §5.3 的表）。
 *
 * <p>为什么用枚举而不是字符串常量：HTTP 状态码与业务码必须成对出现，枚举让"码"和
 * "状态"不可能对不上；新增错误码时先改规范表格、再改这里、再改前端文案（J-17）。
 *
 * <p>message 是给开发者看的，不是给用户看的：前端按 code 分支取自己的文案（T-09），
 * 所以这里的措辞会出现在日志与接口文档里，不要写"操作失败"这种无用信息。
 */
public enum ErrorCode {

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "未登录或 token 无效"),
    SESSION_REVOKED(HttpStatus.UNAUTHORIZED, "会话已失效，请重新登录"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "没有访问该资源的权限"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),
    INVITE_INVALID(HttpStatus.GONE, "邀请链接无效或已失效"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "参数格式错误"),
    // 422 在 RFC 9110 里已改名为 Unprocessable Content，Spring 7 把旧常量标了 deprecated
    RULE_VIOLATED(HttpStatus.UNPROCESSABLE_CONTENT, "请求违反了业务规则"),
    CONFLICT(HttpStatus.CONFLICT, "资源冲突"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁"),
    UPSTREAM_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "上游服务不可用"),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "服务内部错误");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
