package cn.net.mxz.timeimprint.task.web.definition.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TaskDefinitionController public method surface for P02 refactor safety. */
class TaskDefinitionControllerTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(TaskDefinitionController.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("create/1",
                "executeCommand/3",
                "get/1",
                "listDefinitions/5",
                "listScenarios/2",
                "preview/1"), actual);
        assertFalse(actual.isEmpty());
    }
}
