package cn.net.mxz.timeimprint.task.service.application.definition.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test for DefinitionUpdateExecutor. */
class DefinitionUpdateExecutorTest {

    @Test
    void freezesPublicExecuteEntry() {
        List<String> actual = Arrays.stream(DefinitionUpdateExecutor.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("execute/5"), actual);
    }
}
