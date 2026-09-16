package cn.net.mxz.timeimprint.task.service.extension.policy.result;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Owner test: freeze PolicyDecision enum constants for P02 refactor safety. */
class PolicyDecisionTest {

    @Test
    void freezesEnumConstants() {
        String[] actual = Arrays.stream(PolicyDecision.values()).map(Enum::name).toArray(String[]::new);
        assertArrayEquals(new String[] {"ALLOW",
                "DENY",
                "RETRY_LATER"}, actual);
        assertEquals(3, PolicyDecision.values().length);
    }
}
