package cn.net.mxz.timeimprint.task.service.kernel.shared.state;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ControlState enum constants for P02 refactor safety. */
class ControlStateTest {

    @Test
    void freezesEnumConstants() {
        String[] actual = Arrays.stream(ControlState.values()).map(Enum::name).toArray(String[]::new);
        assertArrayEquals(new String[] {"ACTIVE",
                "PAUSED",
                "RETIRED"}, actual);
        assertEquals(3, ControlState.values().length);
    }
}
