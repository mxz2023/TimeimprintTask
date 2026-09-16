package cn.net.mxz.timeimprint.task.service.extension.scenario.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner contract test: freeze ScenarioExtension method surface for P02 refactor safety. */
class ScenarioExtensionContractTest {

    @Test
    void freezesPublicMethodSurface() {
        assertTrue(ScenarioExtension.class.isInterface());
        List<String> actual = Arrays.stream(ScenarioExtension.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("descriptor/0",
                "planInitialDefinition/1",
                "processSignal/1",
                "registrationKey/0",
                "validateDefinitionConfig/1",
                "validateScenarioState/2"), actual);
    }
}
