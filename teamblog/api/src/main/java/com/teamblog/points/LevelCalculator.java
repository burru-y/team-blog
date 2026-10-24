package com.teamblog.points;

/**
 * 等级计算（纯函数，需求 v1.0 §2.4 的等级表）。
 *
 * <p>为什么必须是纯函数：它是全项目最容易被"顺手改一下"的逻辑，而一旦算错，
 * 用户看到的是"我的分涨了但等级没变"。纯函数没有依赖，边界值可以一次测完。
 *
 * <pre>
 * Lv1  0–49
 * Lv2  50–149
 * Lv3  150–299
 * Lv4  300–599
 * Lv5  600+
 * </pre>
 */
public final class LevelCalculator {

    private LevelCalculator() {
        // 工具类（J-27）
    }

    public static int levelOf(int totalPoints) {
        if (totalPoints < 0) {
            return 1;
        }
        if (totalPoints < 50) {
            return 1;
        }
        if (totalPoints < 150) {
            return 2;
        }
        if (totalPoints < 300) {
            return 3;
        }
        if (totalPoints < 600) {
            return 4;
        }
        return 5;
    }

    /** 展示用标签，例如 "Lv3"。 */
    public static String labelOf(int totalPoints) {
        return "Lv" + levelOf(totalPoints);
    }
}
