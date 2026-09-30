package cn.net.mxz.timeimprint.task.common.hashing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * 规范化 UTF-8 字节的 SHA-256 摘要工具。
 */
public final class Sha256 {

    private Sha256() {}

    public static byte[] digestUtf8(String normalized) {
        Objects.requireNonNull(normalized, "normalized");
        return sha256Bytes(normalized.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] sha256Bytes(byte[] input) {
        Objects.requireNonNull(input, "input");
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
