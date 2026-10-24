package com.teamblog.learning.dto;

/**
 * 生成的摘要。
 *
 * <p>只返回文本，不落库：方案要求"生成后填入编辑框，成员点保存才落库"——
 * 自动保存等于替用户决定，而他可能还没看完。
 */
public record SummaryResponse(String summary) {
}