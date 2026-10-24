package com.teamblog.common;

import java.nio.charset.StandardCharsets;

/**
 * 密码规则。
 *
 * <p>为什么必须有长度上限：**BCrypt 只处理前 72 字节**，超出部分被静默截断。
 * 用户以为自己设了 100 位密码，实际只有前 72 位有效——换第 73 位之后的内容，
 * 密码"没变"。
 *
 * <p>为什么按**字节**而不是字符：中文一个字符 3 字节，72 个字符早就超了。
 *
 * <p>规则刻意简单（长度 + 不能等于用户名）：复杂度要求（大小写+符号）的实际收益很小，
 * 反而催生 `Password1!` 这种可预测的密码。
 */
public final class PasswordRules {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_BYTES = 72;

    private PasswordRules() {
        // 工具类（J-27）
    }

    /**
     * @throws BusinessException 不符合规则时返回 400，并说明是哪一条
     */
    public static void validate(String password, String username) {
        if (password == null || password.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "密码不能为空");
        }
        if (password.length() < MIN_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "密码至少 " + MIN_LENGTH + " 位");
        }
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "密码太长（BCrypt 只取前 " + MAX_BYTES + " 字节，当前 " + bytes + "）");
        }
        if (username != null && password.equalsIgnoreCase(username)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "密码不能和用户名相同");
        }
    }

    /** 给测试与文档用：是否符合规则。 */
    public static boolean looksValid(String password) {
        try {
            validate(password, null);
            return true;
        } catch (BusinessException ex) {
            return false;
        }
    }
}
