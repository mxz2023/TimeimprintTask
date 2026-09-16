package cn.net.mxz.timeimprint.task.service.extension.policy.spi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Owner test: freeze PolicyPhase enum constants for P02 refactor safety. */
class PolicyPhaseTest {

    @Test
    void freezesEnumConstants() {
        String[] actual = Arrays.stream(PolicyPhase.values()).map(Enum::name).toArray(String[]::new);
        assertArrayEquals(new String[] {"DEFINITION_READ",
                "INSTANCE_READ",
                "COMMAND_EXECUTE",
                "SIGNAL_PROCESS",
                "ACTION_EXECUTE"}, actual);
        assertEquals(5, PolicyPhase.values().length);
    }
}
