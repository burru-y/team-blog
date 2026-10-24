package com.teamblog.user.dto;

import jakarta.validation.constraints.NotBlank;

/** 修改自己的密码：必须提供旧密码。 */
public record ChangePasswordRequest(
        @NotBlank(message = "不能为空") String oldPassword,
        @NotBlank(message = "不能为空") String newPassword
) {
}