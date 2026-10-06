package cn.net.mxz.timeimprint.task.adapter.feishu.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FeishuAdapterPropertiesTest {

    @Test
    void defaultsUseOfficialBaseUrl() {
        FeishuAdapterProperties p = FeishuAdapterProperties.defaults();
        assertEquals("https://open.feishu.cn", p.baseUrl());
        assertEquals(5, p.timeoutSeconds());
    }

    @Test
    void rejectsBlankBaseUrlAndNonPositiveTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new FeishuAdapterProperties(" ", 5));
        assertThrows(IllegalArgumentException.class, () -> new FeishuAdapterProperties("https://x", 0));
    }
}
