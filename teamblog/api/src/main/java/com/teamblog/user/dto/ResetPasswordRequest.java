package com.teamblog.user.dto;

import jakarta.validation.constraints.NotBlank;

/** 管理员重置密码。长度与 BCrypt 上限由 PasswordRules 统一校验（能给出具体原因）。 */
public record ResetPasswordRequest(@NotBlank(message = "不能为空") String newPassword) {
}