package cn.net.mxz.timeimprint.task.service.extension.action.result;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ActionExecutionMode enum constants for P02 refactor safety. */
class ActionExecutionModeTest {

    @Test
    void freezesEnumConstants() {
        String[] actual = Arrays.stream(ActionExecutionMode.values()).map(Enum::name).toArray(String[]::new);
        assertArrayEquals(new String[] {"LOCAL_TRANSACTIONAL",
                "EXTERNAL"}, actual);
        assertEquals(2, ActionExecutionMode.values().length);
    }
}
