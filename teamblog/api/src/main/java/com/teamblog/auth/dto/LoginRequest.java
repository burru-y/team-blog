package com.teamblog.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求。
 *
 * <p>为什么不用实体接请求体：实体有 passwordHash、tokenVersion 等字段，客户端只要能
 * 构造同名 JSON 就能尝试写入（哪怕现在不绑定，将来有人加个 setter 就成了漏洞）。
 * DTO 是白名单（J-04/J-36）。
 */
public record LoginRequest(
        @NotBlank(message = "不能为空") String username,
        @NotBlank(message = "不能为空") String password
) {
}
