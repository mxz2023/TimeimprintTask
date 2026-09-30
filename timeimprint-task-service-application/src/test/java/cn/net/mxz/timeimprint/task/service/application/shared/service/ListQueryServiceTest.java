package cn.net.mxz.timeimprint.task.service.application.shared.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ListQueryService public method surface for P02 refactor safety. */
class ListQueryServiceTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(ListQueryService.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("asOf/0",
                "getActionJob/1",
                "getSignal/1",
                "listActionJobs/6",
                "listAttempts/1",
                "listDefinitions/5",
                "listInstances/9",
                "listScenarios/2",
                "listTransitions/4"), actual);
        assertFalse(actual.isEmpty());
    }
}
