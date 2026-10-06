package cn.net.mxz.timeimprint.task.adapter.feishu.configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FeishuCredentialsTest {

    @Test
    void rejectsBlankParts() {
        assertThrows(IllegalArgumentException.class, () -> new FeishuCredentials(" ", "s"));
        assertThrows(IllegalArgumentException.class, () -> new FeishuCredentials("a", null));
    }

    @Test
    void toStringNeverLeaksSecret() {
        String text = new FeishuCredentials("cli_1", "super-secret").toString();
        assertTrue(text.contains("cli_1"));
        assertFalse(text.contains("super-secret"));
    }
}
