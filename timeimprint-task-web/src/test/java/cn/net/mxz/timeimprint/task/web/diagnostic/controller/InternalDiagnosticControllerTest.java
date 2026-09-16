package cn.net.mxz.timeimprint.task.web.diagnostic.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze InternalDiagnosticController public method surface for P02 refactor safety. */
class InternalDiagnosticControllerTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(InternalDiagnosticController.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("getActionJob/1",
                "getSignal/1",
                "listActionJobs/6",
                "listTransitions/4",
                "redriveAction/2",
                "redriveSignal/2"), actual);
        assertFalse(actual.isEmpty());
    }
}
