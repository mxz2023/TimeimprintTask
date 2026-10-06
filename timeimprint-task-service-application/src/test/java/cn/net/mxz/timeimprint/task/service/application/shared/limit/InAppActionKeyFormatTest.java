package cn.net.mxz.timeimprint.task.service.application.shared.limit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.common.hashing.Sha256;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** A33: actionKey must not embed raw recipient id (only in hash input); IN_APP canon stays on legacy `in_app_notification` suffix (P05 T02), FEISHU uses `FEISHU`. */
class InAppActionKeyFormatTest {

    @Test
    void actionKeyUsesHashNotRawRecipient() {
        String recipientId = "local-actor-with-sensitive-id";
        String purpose = "INITIAL";
        int slot = 0;
        int gen = 1;
        long instanceId = 42L;
        String canon = instanceId + ":" + purpose + ":" + slot + ":" + gen + ":" + recipientId + ":in_app_notification";
        String actionKey = purpose + ":" + base64Url(Sha256.digestUtf8(canon));
        assertFalse(actionKey.contains(recipientId));
        assertTrue(actionKey.startsWith("INITIAL:"));
        assertTrue(actionKey.length() < 80);
    }

    @Test
    void feishuChannelCanonDiffersFromInApp() {
        String base = "42:INITIAL:0:1:alice:";
        String inApp = base64Url(Sha256.digestUtf8(base + "in_app_notification"));
        String feishu = base64Url(Sha256.digestUtf8(base + "FEISHU"));
        assertNotEquals(inApp, feishu);
        assertFalse(("INITIAL:" + feishu).contains("alice"));
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
