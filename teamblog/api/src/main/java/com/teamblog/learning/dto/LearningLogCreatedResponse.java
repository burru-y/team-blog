package com.teamblog.learning.dto;

/**
 * 发布结果：内容本体 + 这次到底有没有加分。
 *
 * <p>为什么要把"有没有加分"明确告诉前端：每日上限是 3 篇，第 4 篇照样会入库但 0 分。
 * 如果界面只显示"发布成功"，用户会以为被漏算了积分。把
 * {@code counted / points / remainingToday} 一起返回，前端就能提示
 * "今天的计分额度已用完，内容已保留"。
 */
public record LearningLogCreatedResponse(
        LearningLogResponse log,
        boolean counted,
        int points,
        int remainingToday
) {
}
