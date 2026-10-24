package com.teamblog.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 把异常统一翻译成 {@link ApiResponse} 信封，Controller 里不写 try/catch（J-16）。
 *
 * <p>为什么只有三个处理分支：业务失败、参数校验失败、其余全部当内部错误。分支越少，
 * 越不容易出现"某个异常忘了映射，框架默认返回一堆堆栈给前端"。
 *
 * <p>注意：Spring Security 的 401/403 走不到这里——它们在过滤器链里就被拦下了，
 * 由 {@code RestAuthenticationEntryPoint} / {@code RestAccessDeniedHandler} 负责。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.code();
        return ResponseEntity.status(code.status())
                .body(ApiResponse.fail(code.name(), ex.getMessage()));
    }

    /** 429 必须带 Retry-After（A-13）：客户端据此决定"等多久再试"，而不是盲目重试。 */
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitException ex) {
        return ResponseEntity.status(ErrorCode.RATE_LIMITED.status())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
                .body(ApiResponse.fail(ex.code().name(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String firstMessage = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse(ErrorCode.VALIDATION_FAILED.message());
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED.name(), firstMessage));
    }

    /**
     * 请求体不是合法 JSON、或类型对不上（比如给数字字段传了字符串）。
     *
     * <p>必须单独处理：它默认会落到下面的 {@code Exception} 分支变成 500，而客户端
     * 明明有错——500 会让人以为是服务端挂了，排查方向直接跑偏。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED.name(), "请求体格式不正确"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        // 对外只给通用文案，堆栈留在日志里（S-09）
        log.error("未预期的异常", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL.status())
                .body(ApiResponse.fail(ErrorCode.INTERNAL.name(), ErrorCode.INTERNAL.message()));
    }
}
