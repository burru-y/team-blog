package com.teamblog.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 访客留言。
 *
 * <p>{@code website} 是**蜜罐字段**：真实用户看不见它（前端用 CSS 隐藏），
 * 但自动化脚本会把表单里所有 input 都填上。所以只要它非空，就认为提交者是机器人。
 *
 * <p>为什么用 `website` 而不是 `hidden` 之类的名字：机器人也会读字段名，
 * 一个"看起来很合理"的名字更容易骗到它们。
 */
public record FeedbackCreateRequest(
        @NotBlank(message = "不能为空") @Size(max = 50, message = "最多 50 字") String name,
        @Size(max = 255, message = "最多 255 字") String email,
        @NotBlank(message = "不能为空") @Size(max = 2000, message = "最多 2000 字") String content,
        /** 蜜罐：正常用户永远不填。 */
        String website
) {
}
