package com.teamblog.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发布学习内容。
 *
 * <p>{@code type} 用字符串接收（NOTE / CODE），非法值由 Service 转成 400 并给出可选值，
 * 而不是让 Jackson 抛泛型反序列化错误。
 */
public record LearningLogCreateRequest(
        @NotBlank(message = "不能为空") String type,
        @NotBlank(message = "不能为空") @Size(max = 200, message = "最多 200 字") String title,
        @NotBlank(message = "不能为空") String contentMd,
        String summaryMd,
        /** 逗号分隔的标签，最多 5 个；服务端会去重与截断。 */
        @Size(max = 200, message = "标签过长") String tags
) {
}
