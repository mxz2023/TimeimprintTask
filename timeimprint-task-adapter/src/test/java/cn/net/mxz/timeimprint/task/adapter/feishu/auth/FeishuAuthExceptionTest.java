package cn.net.mxz.timeimprint.task.adapter.feishu.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FeishuAuthExceptionTest {

    @Test
    void exposesRetryabilityAndApiCode() {
        FeishuAuthException e = new FeishuAuthException(true, 99991400, "rate", null);
        assertTrue(e.retryable());
        assertEquals(99991400, e.apiCode());
        assertEquals("rate", e.getMessage());
    }
}
