package cn.net.mxz.timeimprint.task.adapter.feishu.callback;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 飞书回调验签（P05 T04）。
 *
 * <ul>
 *   <li><b>Verification Token</b>（必填）：回调明文中 {@code header.token}（事件 2.0）或顶层 {@code token}
 *       （url_verification）必须与配置一致，常量时间比较。
 *   <li><b>Encrypt Key</b>（可选）：配置后额外要求请求头签名
 *       {@code hex(SHA256(timestamp + nonce + encryptKey + body))} 匹配，并支持
 *       {@code {"encrypt":"..."}} 的 AES-256-CBC 解密（密钥 = SHA256(encryptKey)，IV = 密文前 16 字节）。
 * </ul>
 *
 * <p>本类不记录任何密钥或请求体；所有失败路径只返回 false/empty。
 */
public final class DefaultFeishuCardActionVerifier implements FeishuCardActionVerifier {

    private final byte[] verificationToken;
    private final String encryptKey;
    private final JsonMapper mapper;

    /**
     * @param verificationToken 开发者后台的 Verification Token，不得为空
     * @param encryptKey 开发者后台的 Encrypt Key；未开启加密时可为 null 或空白
     */
    public DefaultFeishuCardActionVerifier(String verificationToken, String encryptKey, JsonMapper mapper) {
        if (verificationToken == null || verificationToken.isBlank()) {
            throw new IllegalArgumentException("feishu verification-token must not be blank");
        }
        this.verificationToken = verificationToken.strip().getBytes(StandardCharsets.UTF_8);
        this.encryptKey = encryptKey == null || encryptKey.isBlank() ? null : encryptKey.strip();
        this.mapper = mapper;
    }

    @Override
    public boolean verify(String timestamp, String nonce, String signature, byte[] body) {
        if (body == null || body.length == 0) {
            return false;
        }
        if (encryptKey != null && !signatureMatches(timestamp, nonce, signature, body)) {
            return false;
        }
        Optional<String> plain = openBody(body);
        if (plain.isEmpty()) {
            return false;
        }
        try {
            JsonNode root = mapper.readTree(plain.get());
            JsonNode token = root.path("header").path("token");
            if (!token.isString()) {
                token = root.path("token");
            }
            if (!token.isString()) {
                return false;
            }
            return MessageDigest.isEqual(verificationToken, token.asString().getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public Optional<String> openBody(byte[] body) {
        if (body == null || body.length == 0) {
            return Optional.empty();
        }
        try {
            String text = new String(body, StandardCharsets.UTF_8);
            JsonNode root = mapper.readTree(text);
            if (!root.isObject()) {
                return Optional.empty();
            }
            JsonNode encrypted = root.path("encrypt");
            if (!encrypted.isString()) {
                return Optional.of(text);
            }
            if (encryptKey == null) {
                return Optional.empty();
            }
            return Optional.of(decrypt(encrypted.asString()));
        } catch (RuntimeException | GeneralSecurityException e) {
            return Optional.empty();
        }
    }

    private boolean signatureMatches(String timestamp, String nonce, String signature, byte[] body) {
        if (isBlank(timestamp) || isBlank(nonce) || isBlank(signature)) {
            return false;
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(timestamp.getBytes(StandardCharsets.UTF_8));
            sha.update(nonce.getBytes(StandardCharsets.UTF_8));
            sha.update(encryptKey.getBytes(StandardCharsets.UTF_8));
            sha.update(body);
            byte[] expected = HexFormat.of().formatHex(sha.digest()).getBytes(StandardCharsets.UTF_8);
            byte[] actual = signature.strip().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(expected, actual);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private String decrypt(String base64) throws GeneralSecurityException {
        byte[] raw = Base64.getDecoder().decode(base64);
        if (raw.length <= 16) {
            throw new GeneralSecurityException("ciphertext too short");
        }
        byte[] key = MessageDigest.getInstance("SHA-256").digest(encryptKey.getBytes(StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(Arrays.copyOfRange(raw, 0, 16)));
        return new String(cipher.doFinal(raw, 16, raw.length - 16), StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
