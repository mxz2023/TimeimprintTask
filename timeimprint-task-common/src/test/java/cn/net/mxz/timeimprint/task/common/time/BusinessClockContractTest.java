package cn.net.mxz.timeimprint.task.common.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner contract test: freeze BusinessClock method surface for P02 refactor safety. */
class BusinessClockContractTest {

    @Test
    void freezesPublicMethodSurface() {
        assertTrue(BusinessClock.class.isInterface());
        List<String> actual = Arrays.stream(BusinessClock.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("nowUtcSeconds/0",
                "truncateToUtcSeconds/1"), actual);
    }
}
