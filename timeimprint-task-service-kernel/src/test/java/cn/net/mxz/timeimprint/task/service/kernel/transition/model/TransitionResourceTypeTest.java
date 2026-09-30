package cn.net.mxz.timeimprint.task.service.kernel.transition.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TransitionResourceType enum constants for P02 refactor safety. */
class TransitionResourceTypeTest {

    @Test
    void freezesEnumConstants() {
        String[] actual = Arrays.stream(TransitionResourceType.values()).map(Enum::name).toArray(String[]::new);
        assertArrayEquals(new String[] {"DEFINITION",
                "INSTANCE"}, actual);
        assertEquals(2, TransitionResourceType.values().length);
    }
}
