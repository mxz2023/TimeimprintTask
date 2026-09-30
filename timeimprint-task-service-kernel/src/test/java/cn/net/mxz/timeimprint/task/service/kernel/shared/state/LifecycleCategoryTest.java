package cn.net.mxz.timeimprint.task.service.kernel.shared.state;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Owner test: freeze LifecycleCategory enum constants for P02 refactor safety. */
class LifecycleCategoryTest {

    @Test
    void freezesEnumConstants() {
        String[] actual = Arrays.stream(LifecycleCategory.values()).map(Enum::name).toArray(String[]::new);
        assertArrayEquals(new String[] {"WAITING",
                "ACTIVE",
                "TERMINAL"}, actual);
        assertEquals(3, LifecycleCategory.values().length);
    }
}
