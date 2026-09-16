package cn.net.mxz.timeimprint.task.service.extension.action.result;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ActionHandlerOutcome enum constants for P02 refactor safety. */
class ActionHandlerOutcomeTest {

    @Test
    void freezesEnumConstants() {
        String[] actual = Arrays.stream(ActionHandlerOutcome.values()).map(Enum::name).toArray(String[]::new);
        assertArrayEquals(new String[] {"SUCCEEDED",
                "RETRYABLE_FAILURE",
                "PERMANENT_FAILURE",
                "UNKNOWN"}, actual);
        assertEquals(4, ActionHandlerOutcome.values().length);
    }
}
