package com.teamblog.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 编辑成果。
 *
 * <p>没有 {@code type}：类型决定必填规则，改了类型等于换了一种内容，
 * 历史数据会变得难以解释。要换类型就重新发一条。
 */
public record ProjectUpdateRequest(
        @NotBlank(message = "不能为空") @Size(max = 200, message = "最多 200 字") String title,
        @NotBlank(message = "不能为空") String description,
        @Size(max = 300, message = "最多 300 字") String githubUrl,
        String contentMd,
        @Size(max = 500, message = "最多 500 字") String imageUrl
) {
}
