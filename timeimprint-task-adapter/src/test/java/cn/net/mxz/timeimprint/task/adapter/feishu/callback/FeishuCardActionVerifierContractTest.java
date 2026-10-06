package cn.net.mxz.timeimprint.task.adapter.feishu.callback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 契约：验签仅在签名匹配时返回 true，其余一律 false；openBody 对无法解析的输入返回空。 */
class FeishuCardActionVerifierContractTest {

    private static final FeishuCardActionVerifier VERIFIER = new FeishuCardActionVerifier() {
        @Override
        public boolean verify(String timestamp, String nonce, String signature, byte[] body) {
            return "ok".equals(signature);
        }

        @Override
        public Optional<String> openBody(byte[] body) {
            return body.length == 0 ? Optional.empty() : Optional.of(new String(body));
        }
    };

    @Test
    void acceptsOnlyMatchingSignature() {
        assertTrue(VERIFIER.verify("1", "n", "ok", new byte[0]));
        assertFalse(VERIFIER.verify("1", "n", "bad", new byte[0]));
    }

    @Test
    void openBodyIsEmptyWhenNothingToOpen() {
        assertTrue(VERIFIER.openBody(new byte[0]).isEmpty());
        assertEquals("{}", VERIFIER.openBody("{}".getBytes()).orElseThrow());
    }
}
