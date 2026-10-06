package cn.net.mxz.timeimprint.task.adapter.feishu.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class FeishuApiExceptionTest {

    @Test
    void carriesCategoryCodeAndCause() {
        Throwable cause = new IllegalStateException("x");
        FeishuApiException e = new FeishuApiException(FeishuApiException.Category.UNKNOWN, -1, "m", cause);
        assertEquals(FeishuApiException.Category.UNKNOWN, e.category());
        assertEquals(-1, e.apiCode());
        assertSame(cause, e.getCause());
    }
}
