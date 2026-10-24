package com.teamblog.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 凭邀请注册的请求体。
 *
 * <p>为什么 email 可以为空：邀请本身可能已经指定了收件邮箱（HR 发给特定的人），
 * 那时邮箱以邀请为准，不需要用户再填——也就不能让他改。
 *
 * <p>用户名的规则与数据库 CHECK 保持一致（`^[a-z0-9_]{3,20}$`，J-20 双写）：
 * 注解先给用户可读的提示，数据库兜底。
 */
public record RegisterRequest(
        @NotBlank(message = "不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$", message = "只能是 3-20 位字母、数字或下划线")
        String username,

        @NotBlank(message = "不能为空")
        @Size(min = 8, max = 72, message = "长度需在 8-72 之间")
        String password,

        @Email(message = "格式不正确")
        String email
) {
}
