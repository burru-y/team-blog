package com.teamblog.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 编辑学习内容：标题、正文、摘要可改；类型和业务日期不改。 */
public record LearningLogUpdateRequest(
        @NotBlank(message = "不能为空") @Size(max = 200, message = "最多 200 字") String title,
        @NotBlank(message = "不能为空") String contentMd,
        String summaryMd
) {
}
