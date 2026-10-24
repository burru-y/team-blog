package com.teamblog.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 发表评论。
 *
 * <p>{@code targetType} 用字符串接收，非法值由 Service 转成 400 并列出可选值
 * （和成果、学习内容保持同一套处理方式）。
 */
public record CommentCreateRequest(
        @NotBlank(message = "不能为空") String targetType,
        @NotNull(message = "不能为空") UUID targetId,
        UUID parentId,
        @NotBlank(message = "不能为空") @Size(max = 2000, message = "最多 2000 字") String content
) {
}
