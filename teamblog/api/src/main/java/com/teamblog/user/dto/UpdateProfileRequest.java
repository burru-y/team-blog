package com.teamblog.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改本人资料。
 *
 * <p>全字段可空：只传要改的字段，未传的保持原值（PATCH 语义）。
 *
 * <p>用户名可改，但要满足与注册相同的格式约束。改名会影响 `/u/{username}` 的链接，
 * 旧链接会失效，前端在提交前提示这一点。
 *
 * <p>邮箱不可改：它涉及唯一性与邀请绑定，属于账号迁移问题，不适合用一个 PATCH 完成。
 */
public record UpdateProfileRequest(
        @Size(max = 200, message = "头像地址过长")
        @Pattern(regexp = "^$|^https?://.+", message = "头像必须是 http(s) 地址")
        String avatarUrl,

        @Size(max = 500, message = "简介不能超过 500 字")
        String bio,

        /** 个人博客地址：填了才在个人主页显示为链接。 */
        @Size(max = 300, message = "博客地址过长")
        @Pattern(regexp = "^$|^https?://.+", message = "博客地址必须是 http(s) 链接")
        String blogUrl,

        @Size(max = 255, message = "链接过长")
        @Pattern(regexp = "^$|^https?://.+", message = "链接必须是 http(s) 地址")
        String githubUrl,

        Boolean emailPublic,

        /** 显示名：可以中文、空格。为空表示回退到用户名。 */
        @Size(max = 30, message = "显示名不能超过 30 字")
        String displayName,

        /** 登录名：与注册同一套规则（3 到 20 位小写字母、数字、下划线）。 */
        @Pattern(regexp = "^$|^[A-Za-z0-9_]{3,20}$", message = "用户名只能是 3 到 20 位字母、数字或下划线")
        String username
) {
}
