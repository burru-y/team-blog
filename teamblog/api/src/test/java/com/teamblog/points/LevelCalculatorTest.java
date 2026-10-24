package com.teamblog.points;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 等级边界（实施方案 §4.5 最后一条）。
 *
 * <p>边界值是这类纯函数唯一值得测的地方：0/49/50、149/150、299/300、599/600。
 * 中间值再多测也不会发现 bug。
 */
class LevelCalculatorTest {

    @ParameterizedTest(name = "{0} 分 → Lv{1}")
    @CsvSource({
            "0, 1",
            "1, 1",
            "49, 1",
            "50, 2",
            "51, 2",
            "149, 2",
            "150, 3",
            "299, 3",
            "300, 4",
            "599, 4",
            "600, 5",
            "6000, 5",
    })
    void 等级边界(int points, int expectedLevel) {
        assertThat(LevelCalculator.levelOf(points)).isEqualTo(expectedLevel);
        assertThat(LevelCalculator.labelOf(points)).isEqualTo("Lv" + expectedLevel);
    }

    @ParameterizedTest(name = "负数 {0} 分按 Lv1 处理")
    @CsvSource({"0", "-1", "-999"})
    void 负数积分不会崩(int points) {
        assertThat(LevelCalculator.levelOf(points)).isEqualTo(1);
    }
}
