package cn.net.mxz.timeimprint.task.boot.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ShutdownAdmissionLifecycle public method surface for P02 refactor safety. */
class ShutdownAdmissionLifecycleTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(ShutdownAdmissionLifecycle.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("getPhase/0",
                "isAutoStartup/0",
                "isRunning/0",
                "start/0",
                "stop/0",
                "stop/1"), actual);
        assertFalse(actual.isEmpty());
    }
}
