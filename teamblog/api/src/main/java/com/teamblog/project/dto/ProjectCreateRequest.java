package com.teamblog.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发布成果。
 *
 * <p>{@code type} 用字符串接收而不是枚举：非法值由 Service 转成明确的 400，
 * 而不是让 Jackson 抛一个"泛型反序列化失败"——那种报错对调用方没有指导意义。
 *
 * <p>必填字段按类型不同，这里不写死注解，交给 {@code ProjectService} 校验：
 * 用 {@code @NotBlank} 会把"证书只需要链接或图"这条规则表达成两套 DTO，
 * 反而更难维护。
 */
public record ProjectCreateRequest(
        @NotBlank(message = "不能为空") String type,
        @NotBlank(message = "不能为空") @Size(max = 200, message = "最多 200 字") String title,
        @NotBlank(message = "不能为空") String description,
        @Size(max = 300, message = "最多 300 字") String githubUrl,
        String contentMd,
        @Size(max = 500, message = "最多 500 字") String imageUrl
) {
}
