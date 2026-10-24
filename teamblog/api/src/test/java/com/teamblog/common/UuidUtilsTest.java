package com.teamblog.common;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UUIDv7 的位布局测试。自己实现位运算就必须证明它真的按 RFC 9562 生成，
 * 否则主键"看起来是 UUID"却在排序或索引上退化，而这种退化不会立刻报错。
 */
class UuidUtilsTest {

    @Test
    void v7_版本7且变体为RFC4122() {
        UUID id = UuidUtils.v7();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);   // 10x 二进制
    }

    @Test
    void v7_前48位是生成时刻的毫秒时间戳() {
        long before = System.currentTimeMillis();
        UUID id = UuidUtils.v7();
        long after = System.currentTimeMillis();

        assertThat(timestampMillis(id)).isBetween(before, after);
    }

    @Test
    void v7_批量生成不重复() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(UuidUtils.v7());
        }

        assertThat(ids).hasSize(10_000);
    }

    private static long timestampMillis(UUID id) {
        return id.getMostSignificantBits() >>> 16;
    }
}
