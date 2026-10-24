package com.teamblog.checkin.dto;

import java.time.LocalDate;
import java.util.Map;

/**
 * 热力图数据：日期 → 数值。
 *
 * <p>为什么用 Map 而不是补全的数组：一年 365 天，大多数格子是 0。
 * 前端按"没有这个 key 就是 0"渲染，传输量小一个数量级。
 *
 * <p>个人视角的值只有 0/1（打没打），团队视角是人数。前端用同一套颜色分级，
 * 所以没必要拆成两种响应类型。
 */
public record HeatmapResponse(
        LocalDate from,
        LocalDate to,
        Map<LocalDate, Integer> days
) {
}
