package cn.net.mxz.timeimprint.task.service.extension.action.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner contract test: freeze ActionHandler method surface for P02 refactor safety. */
class ActionHandlerContractTest {

    @Test
    void freezesPublicMethodSurface() {
        assertTrue(ActionHandler.class.isInterface());
        List<String> actual = Arrays.stream(ActionHandler.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("execute/1",
                "executionMode/0",
                "registrationKey/0",
                "supportedSchemaVersions/0",
                "timeoutSeconds/0"), actual);
    }
}
