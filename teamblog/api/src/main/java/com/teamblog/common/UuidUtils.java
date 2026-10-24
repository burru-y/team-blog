package com.teamblog.common;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * UUID 生成工具。存在的原因是：主键要在应用侧生成 UUIDv7。
 *
 * <p>为什么不用 {@code UUID.randomUUID()}（v4）：v4 完全随机，作为主键会导致 B 树索引
 * 页分裂严重、范围查询无意义。v7 前 48 位是毫秒时间戳，插入局部性接近自增，同时保持
 * 不可枚举（剩下 74 位随机）。
 *
 * <p>为什么自己写而不加依赖：只是 8 行位运算。引入 uuid-creator 之类的库要按 X-01
 * 说明许可证与必要性，为一个函数不值得。JDK 17 没有内置 v7（JDK 25 才有）。
 *
 * <p>布局见 RFC 9562 §5.7：
 * <pre>
 *   0                   1                   2                   3
 *   |             unix_ts_ms (48)            | ver(4) | rand_a(12) |
 *   | var(2) |                 rand_b (62)                         |
 * </pre>
 */
public final class UuidUtils {

    private UuidUtils() {
        // 工具类不允许实例化（J-27）
    }

    public static UUID v7() {
        long unixTsMs = System.currentTimeMillis() & 0xFFFF_FFFF_FFFFL;   // 48 位
        long hi = ThreadLocalRandom.current().nextLong();
        long lo = ThreadLocalRandom.current().nextLong();

        long msb = (unixTsMs << 16) | 0x7000L | (hi >>> 52 & 0x0FFFL);    // 版本 7 + rand_a
        long lsb = (lo & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L; // 变体 10 + rand_b

        return new UUID(msb, lsb);
    }
}
