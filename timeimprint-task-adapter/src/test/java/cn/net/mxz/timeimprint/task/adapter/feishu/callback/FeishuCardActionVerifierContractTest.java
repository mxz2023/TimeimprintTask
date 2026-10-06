package cn.net.mxz.timeimprint.task.adapter.feishu.callback;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 契约：验签仅在签名匹配时返回 true，其余一律 false。 */
class FeishuCardActionVerifierContractTest {

    @Test
    void acceptsOnlyMatchingSignature() {
        FeishuCardActionVerifier verifier = (ts, nonce, sig, body) -> "ok".equals(sig);
        assertTrue(verifier.verify("1", "n", "ok", new byte[0]));
        assertFalse(verifier.verify("1", "n", "bad", new byte[0]));
    }
}
