package cn.net.mxz.timeimprint.task.service.extension.trigger.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner contract test: freeze TriggerProvider method surface for P02 refactor safety. */
class TriggerProviderContractTest {

    @Test
    void freezesPublicMethodSurface() {
        assertTrue(TriggerProvider.class.isInterface());
        List<String> actual = Arrays.stream(TriggerProvider.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("evaluate/1",
                "registrationKey/0",
                "supportedSchemaVersions/0"), actual);
    }
}
