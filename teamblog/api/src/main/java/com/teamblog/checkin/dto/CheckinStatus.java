package com.teamblog.checkin.dto;

import java.time.LocalDate;

/** 今日打卡状态：按钮要不要置灰、连续几天、总分与等级。 */
public record CheckinStatus(
        boolean checkedInToday,
        LocalDate today,
        int streak,
        int totalPoints,
        int level
) {
}
