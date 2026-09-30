package cn.net.mxz.timeimprint.task.common.hashing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;

/** Owner test for Sha256. */
class Sha256Test {

    @Test
    void digestsKnownUtf8Vector() throws Exception {
        byte[] expected = MessageDigest.getInstance("SHA-256")
                .digest("timeimprint-task".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(expected, Sha256.digestUtf8("timeimprint-task"));
        assertArrayEquals(expected, Sha256.sha256Bytes("timeimprint-task".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsNullInput() {
        assertThrows(NullPointerException.class, () -> Sha256.digestUtf8(null));
        assertThrows(NullPointerException.class, () -> Sha256.sha256Bytes(null));
    }
}
