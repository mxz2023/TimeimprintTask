package cn.net.mxz.timeimprint.task.service.application.definition.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner contract test: freeze DefinitionUpdatePort method surface for P02 refactor safety. */
class DefinitionUpdatePortContractTest {

    @Test
    void freezesPublicMethodSurface() {
        assertTrue(DefinitionUpdatePort.class.isInterface());
        List<String> actual = Arrays.stream(DefinitionUpdatePort.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("apply/1"), actual);
    }
}
