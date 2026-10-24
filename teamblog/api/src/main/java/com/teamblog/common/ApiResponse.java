package com.teamblog.common;

/**
 * 全服务统一响应信封：{ "ok": true, "data": ..., "error": null }
 * AI 服务（FastAPI）也返回同样的结构，前端只写一套错误处理。
 */
public record ApiResponse<T>(boolean ok, T data, ErrorBody error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message));
    }

    public record ErrorBody(String code, String message) {
    }
}
