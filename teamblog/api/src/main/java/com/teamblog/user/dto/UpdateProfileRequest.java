package com.teamblog.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改本人资料。
 *
 * <p>全字段可空：只传要改的字段，未传的保持原值（PATCH 语义）。
 * 这样前端可以只提交改动项，不必先读回全部字段再整体覆盖。
 *
 * <p>为什么不开放修改用户名与邮箱：用户名是路由的一部分（`/u/{username}`），
 * 改了等于让旧链接全部失效；邮箱涉及唯一性与邀请绑定，属于账号迁移问题，
 * 不适合用一个 PATCH 顺手完成。
 */
public record UpdateProfileRequest(
        @Size(max = 200, message = "头像地址过长")
        @Pattern(regexp = "^$|^https?://.+", message = "头像必须是 http(s) 地址")
        String avatarUrl,

        @Size(max = 500, message = "简介不能超过 500 字")
        String bio,

        @Size(max = 255, message = "链接过长")
        @Pattern(regexp = "^$|^https?://.+", message = "链接必须是 http(s) 地址")
        String githubUrl,

        Boolean emailPublic
) {
}
