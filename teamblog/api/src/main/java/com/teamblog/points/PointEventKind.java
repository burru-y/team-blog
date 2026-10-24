package com.teamblog.points;

/**
 * 积分事件类型。对应 {@code point_events.kind} 的 CHECK。
 *
 * <p>账本是"只加不删"的流水：每条记录代表"某个事件产生过一次积分"，
 * {@code (user_id, kind, ref_id)} 唯一，从数据库层面保证同一件事不能加两次分。
 */
public enum PointEventKind {

    CHECKIN,
    LEARNING_LOG,
    PROJECT,
    STREAK_7,
    STREAK_14,
    STREAK_30
}
