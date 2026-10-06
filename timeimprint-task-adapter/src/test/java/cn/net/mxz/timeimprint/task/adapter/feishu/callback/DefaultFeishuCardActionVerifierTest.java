package cn.net.mxz.timeimprint.task.adapter.feishu.callback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DefaultFeishuCardActionVerifierTest {

    private static final String TOKEN = "v-token-123";
    private static final String KEY = "encrypt-key-xyz";
    private final JsonMapper mapper = JsonMapper.builder().build();

    private static byte[] event(String token) {
        return ("{\"schema\":\"2.0\",\"header\":{\"event_id\":\"e1\",\"token\":\"" + token
                        + "\",\"event_type\":\"card.action.trigger\"},\"event\":{}}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String sign(String ts, String nonce, String key, byte[] body) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update(ts.getBytes(StandardCharsets.UTF_8));
        sha.update(nonce.getBytes(StandardCharsets.UTF_8));
        sha.update(key.getBytes(StandardCharsets.UTF_8));
        sha.update(body);
        return HexFormat.of().formatHex(sha.digest());
    }

    private static byte[] encrypt(String key, String plain) throws Exception {
        byte[] aesKey = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        byte[] iv = new byte[16];
        for (int i = 0; i < iv.length; i++) {
            iv[i] = (byte) (i + 1);
        }
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
        byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] all = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, all, 0, iv.length);
        System.arraycopy(ct, 0, all, iv.length, ct.length);
        return ("{\"encrypt\":\"" + Base64.getEncoder().encodeToString(all) + "\"}")
                .getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void acceptsMatchingVerificationTokenWithoutEncryptKey() {
        var v = new DefaultFeishuCardActionVerifier(TOKEN, null, mapper);
        assertTrue(v.verify(null, null, null, event(TOKEN)));
        assertEquals(new String(event(TOKEN), StandardCharsets.UTF_8), v.openBody(event(TOKEN)).orElseThrow());
    }

    @Test
    void rejectsWrongMissingOrMalformedToken() {
        var v = new DefaultFeishuCardActionVerifier(TOKEN, null, mapper);
        assertFalse(v.verify(null, null, null, event("other")));
        assertFalse(v.verify(null, null, null, "{\"header\":{}}".getBytes(StandardCharsets.UTF_8)));
        assertFalse(v.verify(null, null, null, "not json".getBytes(StandardCharsets.UTF_8)));
        assertFalse(v.verify(null, null, null, "[]".getBytes(StandardCharsets.UTF_8)));
        assertFalse(v.verify(null, null, null, new byte[0]));
        assertFalse(v.verify(null, null, null, null));
    }

    @Test
    void acceptsTopLevelTokenForUrlVerification() {
        var v = new DefaultFeishuCardActionVerifier(TOKEN, null, mapper);
        byte[] body = ("{\"challenge\":\"c1\",\"token\":\"" + TOKEN + "\",\"type\":\"url_verification\"}")
                .getBytes(StandardCharsets.UTF_8);
        assertTrue(v.verify(null, null, null, body));
        byte[] bad = "{\"challenge\":\"c1\",\"token\":\"x\",\"type\":\"url_verification\"}".getBytes(StandardCharsets.UTF_8);
        assertFalse(v.verify(null, null, null, bad));
    }

    @Test
    void requiresMatchingSignatureWhenEncryptKeyConfigured() throws Exception {
        var v = new DefaultFeishuCardActionVerifier(TOKEN, KEY, mapper);
        byte[] body = event(TOKEN);
        String good = sign("1700000000", "n1", KEY, body);
        assertTrue(v.verify("1700000000", "n1", good, body));
        assertTrue(v.verify("1700000000", "n1", good.toUpperCase(), body));
        assertFalse(v.verify("1700000000", "n1", "deadbeef", body));
        assertFalse(v.verify("1700000001", "n1", good, body));
        assertFalse(v.verify("1700000000", "n2", good, body));
        assertFalse(v.verify("1700000000", "n1", good, event(TOKEN + " ")));
        assertFalse(v.verify(null, "n1", good, body));
        assertFalse(v.verify("1700000000", null, good, body));
        assertFalse(v.verify("1700000000", "n1", null, body));
        assertFalse(v.verify("1700000000", "n1", sign("1700000000", "n1", "wrong-key", body), body));
    }

    @Test
    void signatureValidButTokenWrongStillRejected() throws Exception {
        var v = new DefaultFeishuCardActionVerifier(TOKEN, KEY, mapper);
        byte[] body = event("forged");
        assertFalse(v.verify("1", "n", sign("1", "n", KEY, body), body));
    }

    @Test
    void decryptsEncryptedBodyAndChecksTokenInside() throws Exception {
        var v = new DefaultFeishuCardActionVerifier(TOKEN, KEY, mapper);
        byte[] enc = encrypt(KEY, new String(event(TOKEN), StandardCharsets.UTF_8));
        assertEquals(new String(event(TOKEN), StandardCharsets.UTF_8), v.openBody(enc).orElseThrow());
        assertTrue(v.verify("1", "n", sign("1", "n", KEY, enc), enc));

        byte[] forged = encrypt(KEY, new String(event("forged"), StandardCharsets.UTF_8));
        assertFalse(v.verify("1", "n", sign("1", "n", KEY, forged), forged));
    }

    @Test
    void encryptedBodyWithoutKeyOrWithWrongKeyIsRejected() throws Exception {
        byte[] enc = encrypt(KEY, new String(event(TOKEN), StandardCharsets.UTF_8));
        var noKey = new DefaultFeishuCardActionVerifier(TOKEN, "  ", mapper);
        assertTrue(noKey.openBody(enc).isEmpty());
        assertFalse(noKey.verify(null, null, null, enc));
        var wrongKey = new DefaultFeishuCardActionVerifier(TOKEN, "other-key", mapper);
        assertTrue(wrongKey.openBody(enc).isEmpty());
        assertFalse(wrongKey.verify("1", "n", sign("1", "n", "other-key", enc), enc));
        assertTrue(noKey.openBody("{\"encrypt\":\"AAAA\"}".getBytes(StandardCharsets.UTF_8)).isEmpty());
        assertTrue(new DefaultFeishuCardActionVerifier(TOKEN, KEY, mapper)
                .openBody("{\"encrypt\":\"AAAA\"}".getBytes(StandardCharsets.UTF_8))
                .isEmpty());
    }

    @Test
    void blankVerificationTokenIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new DefaultFeishuCardActionVerifier(" ", null, mapper));
        assertThrows(IllegalArgumentException.class, () -> new DefaultFeishuCardActionVerifier(null, null, mapper));
    }
}
