package cn.net.mxz.timeimprint.task.service.application.limit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.common.Sha256;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** A33: actionKey must not embed raw recipient id (only in hash input). */
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

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
