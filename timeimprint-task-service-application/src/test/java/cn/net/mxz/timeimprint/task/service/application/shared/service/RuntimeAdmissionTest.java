package cn.net.mxz.timeimprint.task.service.application.shared.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze RuntimeAdmission public method surface for P02 refactor safety. */
class RuntimeAdmissionTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(RuntimeAdmission.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("acceptingClaims/0",
                "acceptingWrites/0",
                "beginShutdown/0",
                "resetForTests/0",
                "suspendClaimsForTests/0"), actual);
        assertFalse(actual.isEmpty());
    }
}
