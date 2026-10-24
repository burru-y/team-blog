package com.teamblog.checkin.dto;

import java.time.LocalDate;

/**
 * 打卡结果。
 *
 * <p>把 streak / gained / totalPoints 一起返回，是为了前端"打卡后不刷新页面也能立刻更新数字"——
 * 再发一次请求会有半秒的空窗，体验上就是"点了没反应"。
 */
public record CheckinResult(
        LocalDate checkinDate,
        int streak,
        int gained,
        int totalPoints,
        int level
) {
}
