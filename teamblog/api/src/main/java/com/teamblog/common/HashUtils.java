package com.teamblog.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 摘要工具。
 *
 * <p>为什么单独抽出来：refresh token 与 IP 都要"只存哈希，不存明文"（S-04 / P-14）。
 * 两处各写一遍 SHA-256，迟早有一处漏了编码或字节集，排查起来极难。
 *
 * <p>为什么用 SHA-256 而不是 BCrypt：这两个东西本身是**高熵随机值/地址**，不存在
 * 弱口令，不需要慢哈希；慢哈希只用于"人想出来的密码"。
 */
public final class HashUtils {

    private HashUtils() {
        // 工具类不允许实例化（J-27）
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM 不支持 SHA-256", ex);
        }
    }
}
