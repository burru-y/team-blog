package com.teamblog.auth;

import com.teamblog.common.ApiResponse;
import com.teamblog.common.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 在过滤器链里写错误响应。
 *
 * <p>为什么要专门一个组件：401/403 发生在 Spring MVC 之前，{@code @RestControllerAdvice}
 * 还没机会介入，只能自己写 response。如果三处（过滤器、EntryPoint、AccessDeniedHandler）
 * 各写一遍，信封格式迟早会不一致，而前端只写了一套解析（A-03）。
 */
@Component
public class ErrorResponder {

    private final ObjectMapper objectMapper;

    public ErrorResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, ErrorCode code) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.fail(code.name(), code.message()));
    }
}
